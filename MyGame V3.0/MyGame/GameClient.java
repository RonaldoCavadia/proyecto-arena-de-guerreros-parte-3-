// GameClient.java - VERSIÓN CON PROGRAMACIÓN FUNCIONAL
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.*;
import java.util.stream.*;

public class GameClient {
    private static final AtomicBoolean showMenuFlag = new AtomicBoolean(true);
    
    // Mapa de procesadores de mensajes del servidor
    private static final Map<Predicate<String>, Consumer<String>> messageProcessors = Map.of(
        msg -> msg.startsWith("HP:"), 
            msg -> System.out.println("Vida actual: " + msg.substring(3)),
        
        msg -> msg.startsWith("HEALED:"), 
            msg -> System.out.println("Te curaste " + msg.substring(7) + " puntos de vida"),
        
        msg -> msg.equals("YOU_DIED"), 
            msg -> System.out.println("Has muerto! Usa HEAL para recuperarte."),
        
        msg -> msg.startsWith("WELCOME"), 
            msg -> System.out.println(msg),
        
        msg -> msg.startsWith("BATTLE_START:"), 
            msg -> {
                System.out.println("Batalla iniciada! " + msg.substring(13));
                showMenuFlag.set(false);
            },
        
        msg -> msg.equals("YOU_WIN"), 
            msg -> System.out.println("Ganaste la batalla!"),
        
        msg -> msg.startsWith("CHALLENGE_REQUEST:"), 
            msg -> processChallengeRequest(msg),
        
        msg -> msg.equals("BATTLE_END"), 
            msg -> {
                showMenuFlag.set(true);
                System.out.println("Batalla terminada");
            }
    );
    
    // Comandos que ocultan el menú
    private static final Set<String> MENU_HIDING_COMMANDS = Set.of(
        "9", "HELP", "5", "WEAPONS", "3", "STATUS", "4", "PLAYERS"
    );

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;
        
        System.out.println("Conectando al servidor " + host + ":" + port + "...");
        
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
             Scanner sc = new Scanner(System.in)) {
            
            System.out.println("Conectado exitosamente!");
            
            // Hilo para recibir mensajes del servidor
            startMessageReaderThread(in);
            
            // Obtener y enviar nombre del jugador
            getPlayerName(sc)
                .ifPresent(playerName -> out.println(playerName));
            
            // Bucle principal de comandos
            runCommandLoop(sc, out);
            
        } catch (IOException e) {
            System.err.println("Error de conexión: " + e.getMessage());
        }
    }
    
    private static void startMessageReaderThread(BufferedReader in) {
        Thread readerThread = new Thread(() -> {
            Stream.generate(() -> {
                try {
                    return in.readLine();
                } catch (IOException e) {
                    return null;
                }
            })
            .takeWhile(Objects::nonNull)
            .forEach(GameClient::processServerMessage);
            
            System.out.println("Desconectado del servidor");
        });
        readerThread.start();
    }
    
    private static Optional<String> getPlayerName(Scanner sc) {
        System.out.print("Ingresa tu nombre de jugador: ");
        return Optional.of(sc.nextLine())
            .map(String::trim)
            .filter(name -> !name.isEmpty())
            .or(() -> {
                System.out.println("Nombre no válido, intenta de nuevo");
                return getPlayerName(sc); // Recursión funcional
            });
    }
    
    private static void runCommandLoop(Scanner sc, PrintWriter out) {
        Stream.generate(sc::nextLine)
            .takeWhile(command -> !isExitCommand(command))
            .forEach(command -> processClientCommand(command, out));
        
        System.out.println("Hasta luego!");
    }
    
    private static boolean isExitCommand(String command) {
        return Stream.of("EXIT", "0")
            .anyMatch(exitCmd -> exitCmd.equalsIgnoreCase(command));
    }
    
    private static void processClientCommand(String command, PrintWriter out) {
        Optional.of(command)
            .map(String::trim)
            .filter(cmd -> !cmd.isEmpty())
            .ifPresent(cmd -> {
                if (isClearCommand(cmd)) {
                    System.out.print("\033[2J\033[1;1H");
                } else {
                    out.println(cmd);
                    updateMenuFlag(cmd);
                }
            });
    }
    
    private static boolean isClearCommand(String command) {
        return Stream.of("CLEAR", "CLS")
            .anyMatch(clearCmd -> clearCmd.equalsIgnoreCase(command));
    }
    
    private static void processServerMessage(String message) {
        messageProcessors.entrySet().stream()
            .filter(entry -> entry.getKey().test(message))
            .map(Map.Entry::getValue)
            .findFirst()
            .ifPresentOrElse(
                processor -> processor.accept(message),
                () -> System.out.println(message) // Mensaje por defecto
            );
    }
    
    private static void processChallengeRequest(String message) {
        String challenger = message.substring(18);
        System.out.println(challenger + " te ha desafiado a una batalla!");
        System.out.println("   Escribe 'ACCEPT:" + challenger + "' para aceptar");
    }
    
    private static void updateMenuFlag(String command) {
        boolean shouldHideMenu = MENU_HIDING_COMMANDS.stream()
            .anyMatch(cmd -> cmd.equalsIgnoreCase(command));
        
        showMenuFlag.set(!shouldHideMenu);
    }
    
    private static void displayMenuIfNeeded() {
        if (showMenuFlag.get()) {
            String menu = Stream.of(
                "\n" + "=".repeat(50),
                "Qué quieres hacer? (escribe el número o comando completo)",
                "=".repeat(50)
            ).collect(Collectors.joining("\n"));
            
            System.out.println(menu);
        }
    }
}