// GameServer.java - VERSIÓN SIMPLIFICADA Y FUNCIONAL
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class GameServer {
    private static final int PORT = 5000;
    private static final CopyOnWriteArrayList<ClientHandler> players = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) {
        System.out.println("=== SERVIDOR DE JUEGO INICIADO ===");
        System.out.println("Esperando conexiones en puerto " + PORT + "...\n");
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuevo cliente conectado desde: " + 
                    clientSocket.getRemoteSocketAddress());
                
                // Assuming you have an Enemy list, create or obtain it here
                List<Enemy> enemies = new ArrayList<>(); // Replace with your actual enemy list if needed
                ClientHandler clientHandler = new ClientHandler(clientSocket);
                players.add(clientHandler);
                clientHandler.start();
                
                System.out.println("Total de jugadores conectados: " + players.size());
            }
        } catch (IOException e) {
            System.err.println("Error en el servidor: " + e.getMessage());
        }
    }
    
    public static List<ClientHandler> getPlayers() {
        return new ArrayList<>(players);
    }
}