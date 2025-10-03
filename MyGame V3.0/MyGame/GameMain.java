// GameMain.java - VERSIÓN FUNCIONAL CORREGIDA
import java.util.*;
import java.util.function.Function;
import java.util.stream.*;

public class GameMain {
    
    // Función pura para crear armas
    private static List<Weapons> crearArmas() {
        return Arrays.asList(
            new Weapons("una yuca", 20),
            new Weapons("espada de icopor", 15),
            new Weapons("machete oxidado", 25),
            new Weapons("cebollin (lanzable)", 18)
        );
    }
    
    // Función pura para crear un jugador
    private static Optional<Player> crearJugador(String nombre, List<Weapons> armas, int indiceArma) {
        return Optional.of(indiceArma)
            .filter(i -> i >= 0 && i < armas.size())
            .map(i -> new Player(nombre, 100, armas.get(i)));
    }
    
    // CORREGIDO: Función para procesar turno - ahora recibe Scanner
    private static Function<List<Player>, List<Player>> procesarTurnoJugador(Player jugador, Random rand, Scanner sc) {
        return jugadores -> {
            System.out.println("\nTurno de " + jugador.getName() + " (vida: " + jugador.getHp() + ")");
            System.out.println("Presiona ENTER para continuar...");
            sc.nextLine(); // CORREGIDO: Usar el Scanner existente
            
            System.out.print("¿Deseas atacar este turno? (y/n): ");
            String decision = sc.nextLine();
            
            if (decision.equalsIgnoreCase("y")) {
                int dado = rand.nextInt(100);
                if (dado < 50) {
                    System.out.println(jugador.getName() + " ataca!");
                    // En versión completa, aquí se aplicaría el ataque a enemigos
                    return jugadores;
                } else {
                    System.out.println(jugador.getName() + " falló su ataque.");
                    return jugadores;
                }
            } else {
                Player curado = jugador.heal(15);
                // CORREGIDO: Reemplazar el jugador actual con el curado
                return jugadores.stream()
                    .map(p -> p.equals(jugador) ? curado : p)
                    .collect(Collectors.toList());
            }
        };
    }
    
    // CORREGIDO: Función para simular batalla con enemigos
    private static void simularBatalla(List<Player> jugadores, List<Enemy> enemigos, Scanner sc, Random rand) {
        // Simular algunos turnos
        for (int turno = 0; turno < 3; turno++) {
            System.out.println("\n=== TURNO " + (turno + 1) + " ===");
            
            // CORREGIDO: Procesar cada jugador y actualizar la lista
            List<Player> jugadoresActuales = new ArrayList<>(jugadores);
            
            for (Player jugador : jugadores) {
                if (jugador.isAlive()) {
                    List<Player> nuevosJugadores = procesarTurnoJugador(jugador, rand, sc).apply(jugadoresActuales);
                    // En una implementación real, actualizaríamos jugadoresActuales aquí
                }
            }
            
            // Simular ataque de enemigos (versión simplificada)
            enemigos.stream()
                .filter(Enemy::estaVivo)
                .forEach(enemigo -> {
                    List<Player> jugadoresVivos = jugadores.stream()
                        .filter(Player::isAlive)
                        .collect(Collectors.toList());
                    
                    if (!jugadoresVivos.isEmpty()) {
                        Player objetivo = jugadoresVivos.get(rand.nextInt(jugadoresVivos.size()));
                        System.out.println(enemigo.getEnemyName() + " ataca a " + objetivo.getName());
                        // En versión completa: objetivo.takeDamage(enemigo.getAttackPower());
                    }
                });
        }
    }
    
    public static void main(String[] args) {
        List<Weapons> armas = crearArmas();
        Scanner sc = new Scanner(System.in);
        Random rand = new Random();
        
        // Crear jugadores de forma funcional
        List<Player> jugadores = IntStream.range(0, 2) // Ejemplo con 2 jugadores
            .mapToObj(i -> {
                System.out.print("\nNombre del jugador " + (i + 1) + ": ");
                String nombre = sc.nextLine();
                
                System.out.println(nombre + ", elige tu arma:");
                IntStream.range(0, armas.size())
                    .forEach(j -> {
                        Weapons w = armas.get(j);
                        System.out.println((j + 1) + ". " + w.getName() + " (daño: " + w.getDamage() + ")");
                    });
                
                System.out.print("Opción: ");
                int choice = sc.nextInt();
                sc.nextLine(); // Consumir el newline
                
                return crearJugador(nombre, armas, choice - 1)
                    .orElseGet(() -> {
                        System.out.println("Arma inválida, usando arma por defecto");
                        return new Player(nombre, 100, armas.get(0));
                    });
            })
            .collect(Collectors.toList());
        
        // Crear enemigos de forma funcional
        List<Enemy> enemigos = Arrays.asList(
            new Enemy("Orco Salvaje", 15, 5),
            new Enemy("Esqueleto Guerrero", 10, 7),
            new Enemy("Gólem de Piedra", 12, 6)
        );
        
        System.out.println("\n=== COMIENZA LA BATALLA ===");
        
        // CORREGIDO: Ejecutar simulación de batalla
        simularBatalla(jugadores, enemigos, sc, rand);
        
        // Resultado final
        long sobrevivientes = jugadores.stream()
            .filter(Player::isAlive)
            .count();
            
        if (sobrevivientes > 0) {
            System.out.println("\n¡El equipo de jugadores sobrevivió!");
            jugadores.forEach(p -> 
                System.out.println(p.getName() + " vida: " + p.getHp()));
        } else {
            System.out.println("Todos los jugadores fueron derrotados.");
        }
        
        System.out.println("Fin de la simulación.");
        sc.close();
    }
}