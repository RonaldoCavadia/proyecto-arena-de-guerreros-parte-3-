// ClientHandler.java - VERSIÓN CON SISTEMA DE ESTADÍSTICAS Y DESCONEXIÓN AUTOMÁTICA
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.*;

public class ClientHandler extends Thread {
    private static final List<Weapons> AVAILABLE_WEAPONS = List.of(
        new Weapons("una yuca", 20),
        new Weapons("el poder de la amistad", 15),
        new Weapons("machete oxidado", 25),
        new Weapons("$800 de cebollin", 18),
        new Weapons("hueso de pollo", 22)
    );

    // Lista sincronizada de jugadores
    private static final CopyOnWriteArrayList<ClientHandler> allPlayers = new CopyOnWriteArrayList<>();
    
    // Sistema de estadísticas global
    private static final Map<String, PlayerStats> globalStats = new ConcurrentHashMap<>();
    
    private final Socket socket;
    private final BufferedReader in;
    private final PrintWriter out;
    private static final StatsProcessor statsProcessor = new StatsProcessor();
    private long battleStartTime;
    private int totalDamageDealt = 0;
    
    // Estado del jugador
    private final AtomicReference<String> playerName = new AtomicReference<>();
    private final AtomicReference<Integer> hp = new AtomicReference<>(100);
    private final AtomicReference<Weapons> weapon = new AtomicReference<>();
    private final AtomicReference<Boolean> inBattle = new AtomicReference<>(false);
    private final AtomicReference<Boolean> inWeaponMenu = new AtomicReference<>(false);
    private final AtomicReference<ClientHandler> opponent = new AtomicReference<>();
    
    // Estadísticas individuales del jugador
    private final AtomicReference<Integer> kills = new AtomicReference<>(0);
    private final AtomicReference<Integer> deaths = new AtomicReference<>(0);
    private final AtomicReference<Integer> totalDamage = new AtomicReference<>(0);

    // Mapa de comandos de batalla
    private final Map<String, Runnable> battleCommands = Map.of(
        "ATTACK", this::attackOpponent,
        "1", this::attackOpponent,
        "HEAL", this::processHeal,
        "2", this::processHeal,
        "SURRENDER", this::surrenderBattle,
        "S", this::surrenderBattle,
        "STATS", this::showPlayerStats,
        "STATUS", this::showPlayerStats
    );

    // Mapa de acciones
    private final Map<String, Runnable> actionCommands = Map.of(
        "ATTACK", this::processAttack,
        "1", this::processAttack,
        "HEAL", this::processHeal,
        "2", this::processHeal,
        "STATS", this::showPlayerStats,
        "STATUS", this::showPlayerStats
    );

    // Mapa de comandos de información
    private final Map<String, Runnable> infoCommands;
    {
        infoCommands = new HashMap<>();
        infoCommands.put("STATUS", this::processStatus);
        infoCommands.put("3", this::processStatus);
        infoCommands.put("PLAYERS", this::processPlayers);
        infoCommands.put("4", this::processPlayers);
        infoCommands.put("RESET_ENEMIES", this::processResetEnemies);
        infoCommands.put("8", this::processResetEnemies);
        infoCommands.put("HELP", this::sendMainMenu);
        infoCommands.put("9", this::sendMainMenu);
        infoCommands.put("STATS", this::showPlayerStats);
        infoCommands.put("LEADERBOARD", this::showLeaderboard);
        infoCommands.put("LB", this::showLeaderboard);
    }

    public ClientHandler(Socket socket) throws IOException {
        this.socket = socket;
        this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        this.out = new PrintWriter(socket.getOutputStream(), true);
        allPlayers.add(this);
    }

    @Override
    public void run() {
        try {
            sendMessage("CONNECTED_TO_SERVER");
            requestPlayerName();

            // Procesa comandos usando streams
            in.lines()
                .takeWhile(line -> !line.equals("EXIT") && !line.equals("0") && isAlive())
                .forEach(this::processCommand);

        } catch (Exception e) {
            System.out.println("Error en handler para " + playerName.get() + ": " + e.getMessage());
        } finally {
            cleanup();
        }
    }

    private void requestPlayerName() {
        sendMessage("Por favor, ingresa tu nombre de jugador:");
        try {
            in.lines()
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .filter(this::isNameUnique)
                .findFirst()
                .ifPresent(name -> {
                    playerName.set(name);
                    initializePlayerStats(name);
                    sendMessage("WELCOME " + name);
                    System.out.println("Jugador registrado: " + name);
                    sendMainMenu();
                });
        } catch (Exception e) {
            System.out.println("Error leyendo nombre: " + e.getMessage());
        }
    }

    // Inicializa las estadísticas del jugador
    private void initializePlayerStats(String name) {
        globalStats.computeIfAbsent(name, k -> new PlayerStats(name));
        kills.set(0);
        deaths.set(0);
        totalDamage.set(0);
    }

    // Actualiza estadísticas globales
    private void updateGlobalStats() {
        Optional.ofNullable(playerName.get())
            .ifPresent(name -> {
                PlayerStats stats = globalStats.get(name);
                if (stats != null) {
                    stats.updateStats(kills.get(), deaths.get(), totalDamage.get());
                }
            });
    }

    private boolean isNameUnique(String name) {
        return allPlayers.stream()
            .filter(p -> p != this && p.getPlayerName() != null)
            .noneMatch(p -> p.getPlayerName().equalsIgnoreCase(name));
    }

    private void processCommand(String line) {
        System.out.println("Recibido de " + 
            Optional.ofNullable(playerName.get()).orElse("cliente") + ": " + line);

        // Verifica si el jugador está muerto
        if (!isPlayerAlive() && !line.equalsIgnoreCase("HEAL")) {
            sendMessage("ESTÁS MUERTO! Usa 'HEAL' para revivir o 'EXIT' para salir.");
            return;
        }

        Optional.of(line)
            .filter(cmd -> inWeaponMenu.get())
            .ifPresentOrElse(
                cmd -> processWeaponMenuCommand(cmd).run(),
                () -> processRegularCommand(line)
            );
    }

    private void processRegularCommand(String line) {
        Optional.of(line)
            .filter(cmd -> inBattle.get())
            .flatMap(this::processBattleCommand)
            .or(() -> processWeaponCommand(line))
            .or(() -> processActionCommand(line))
            .or(() -> processChallengeCommand(line))
            .or(() -> processInfoCommand(line))
            .ifPresentOrElse(
                Runnable::run,
                () -> sendMessage("UNKNOWN_COMMAND - Usa '9' o 'HELP' para ver comandos")
            );
    }

    private Optional<Runnable> processBattleCommand(String line) {
        return Optional.ofNullable(opponent.get())
            .filter(opp -> inBattle.get())
            .map(opp -> battleCommands.get(line.toUpperCase()))
            .filter(Objects::nonNull);
    }

    private void attackOpponent() {
        Optional.ofNullable(opponent.get())
            .filter(ClientHandler::isInBattle)
            .ifPresentOrElse(
                target -> {
                    int damage = Optional.ofNullable(weapon.get())
                        .map(Weapons::getDamage)
                        .orElse(10);
                    
                    // Actualiza daño total
                    totalDamage.updateAndGet(current -> current + damage);
                    totalDamageDealt += damage;
                    
                    target.takeDamage(damage, this);
                    sendMessage("YOU_ATTACKED_OPPONENT:" + damage);
                    sendMessage("HP_OPPONENT:" + target.getHp());

                    Optional.of(target)
                        .filter(t -> t.getHp() <= 0)
                        .ifPresentOrElse(
                            t -> {
                                // Jugador obtiene un kill
                                kills.updateAndGet(current -> current + 1);
                                sendMessage("YOU_WIN");
                                sendMessage("¡Obtuviste un KILL! Kills totales: " + kills.get());
                                t.sendMessage("YOU_LOSE");
                                t.recordDeath(); // Oponente registra muerte
                                endBattleWithKill(t);
                            },
                            target::sendBattleMenu
                        );
                },
                () -> sendMessage("ERROR: No tienes un oponente válido")
            );
    }

    private void takeDamage(int amount, ClientHandler attacker) {
        hp.updateAndGet(currentHp -> Math.max(0, currentHp - amount));
        sendMessage("HP:" + hp.get());
        sendMessage("RECIBISTE_ATAQUE:" + amount + " de " + attacker.getPlayerName());
        
        Optional.of(hp.get())
            .filter(health -> health <= 0)
            .ifPresent(health -> {
                sendMessage("YOU_DIED");
                recordDeath();
                // Notificar al atacante que obtuvo el kill (ya se hizo en attackOpponent)
            });
    }

    // Registra una muerte y actualiza estadísticas
    private void recordDeath() {
        deaths.updateAndGet(current -> current + 1);
        updateGlobalStats();
        
        // Si está en batalla, terminar la batalla automáticamente
        if (inBattle.get()) {
            Optional.ofNullable(opponent.get())
                .ifPresent(opp -> {
                    opp.sendMessage("TU_OPONENTE_HA_MUERTO");
                    opp.endBattle();
                });
            endBattle();
        }
        
        sendMessage("¡Has muerto! Muertes totales: " + deaths.get());
        sendMessage("Usa 'HEAL' para revivir o 'EXIT' para salir.");
    }

    private void endBattleWithKill(ClientHandler defeatedPlayer) {
        long duration = System.currentTimeMillis() - battleStartTime;
        
        Optional.ofNullable(opponent.get())
            .ifPresent(opp -> {
                MatchResult result = new MatchResult(
                    playerName.get(),
                    opp.getPlayerName(),
                    totalDamageDealt,
                    opp.totalDamageDealt,
                    duration,
                    Optional.ofNullable(weapon.get()).map(Weapons::getName).orElse("Sin arma"),
                    Optional.ofNullable(opp.getWeapon()).map(Weapons::getName).orElse("Sin arma"),
                    false
                );
                statsProcessor.addMatchResult(result);
            });
        
        // Actualizar estadísticas globales
        updateGlobalStats();
        defeatedPlayer.updateGlobalStats();
        
        endBattle();
    }

    private void surrenderBattle() {
        sendMessage("TE_HAS_RENDIDO");
        Optional.ofNullable(opponent.get())
            .ifPresent(opp -> {
                opp.sendMessage("TU_OPONENTE_SE_HA_RENDIDO");
                opp.endBattle();
            });
        endBattle();
    }

    private void endBattle() {
        inBattle.set(false);
        opponent.set(null);
        battleStartTime = 0;
        totalDamageDealt = 0;
        sendMessage("BATTLE_END");
        
        // Si el jugador murió durante la batalla, no mostrar menú principal
        if (isPlayerAlive()) {
            sendMainMenu();
        }
    }

    private void sendBattleMenu() {
        String battleMenu = """
            === BATALLA PVP ===
            1 - ATTACK    - Atacar a tu oponente
            2 - HEAL      - Curarse 15 HP
            S - SURRENDER - Rendirse
            STATS        - Ver tus estadísticas
            ===================
            Tu HP: %d | HP Oponente: %d
            """.formatted(hp.get(), 
                Optional.ofNullable(opponent.get())
                    .map(ClientHandler::getHp)
                    .orElse(0));
        sendMessage(battleMenu);
    }

    // Muestra estadísticas del jugador
    private void showPlayerStats() {
        String stats = String.format("""
            === TUS ESTADÍSTICAS ===
            Nombre: %s
            HP: %d/100
            Kills: %d
            Muertes: %d
            Daño Total: %d
            K/D Ratio: %.2f
            Arma: %s
            ========================
            """,
            playerName.get(),
            hp.get(),
            kills.get(),
            deaths.get(),
            totalDamage.get(),
            deaths.get() > 0 ? (double) kills.get() / deaths.get() : kills.get(),
            Optional.ofNullable(weapon.get())
                .map(w -> w.getName() + " (Daño: " + w.getDamage() + ")")
                .orElse("Ninguna")
        );
        sendMessage(stats);
    }

    // Muestra leaderboard global
    private void showLeaderboard() {
        StringBuilder leaderboard = new StringBuilder();
        leaderboard.append("=== LEADERBOARD GLOBAL ===\n");
        
        globalStats.values().stream()
            .sorted(Comparator.comparingDouble(PlayerStats::getKDRatio).reversed())
            .limit(10)
            .forEach(stats -> leaderboard.append(stats.toString()).append("\n"));
        
        leaderboard.append("==========================");
        sendMessage(leaderboard.toString());
    }

    private Optional<Runnable> processChallengeCommand(String line) {
        return Stream.of(
                Map.entry("CHALLENGE:", (Consumer<String>) this::processChallenge),
                Map.entry("ACCEPT:", (Consumer<String>) this::processAccept)
            )
            .filter(entry -> line.startsWith(entry.getKey()))
            .findFirst()
            .map(entry -> (Runnable) () -> 
                entry.getValue().accept(line.substring(entry.getKey().length()).trim())
            );
    }

    private void processChallenge(String targetName) {
        Optional.of(inBattle.get())
            .filter(battle -> !battle)
            .ifPresentOrElse(
                battle -> findPlayerByName(targetName)
                    .ifPresentOrElse(
                        this::sendChallengeToPlayer,
                        () -> sendMessage("ERROR: Jugador '" + targetName + "' no encontrado. Usa PLAYERS para ver lista.")
                    ),
                () -> sendMessage("ERROR: Ya estás en una batalla")
            );
    }

    private Optional<ClientHandler> findPlayerByName(String name) {
        return allPlayers.stream()
            .filter(p -> p != this)
            .filter(p -> p.getPlayerName() != null)
            .filter(p -> p.getPlayerName().equalsIgnoreCase(name))
            .findFirst();
    }

    private void sendChallengeToPlayer(ClientHandler target) {
        Optional.of(target)
            .filter(t -> !t.isInBattle())
            .ifPresentOrElse(
                t -> {
                    t.sendMessage("CHALLENGE_REQUEST:" + playerName.get());
                    sendMessage("CHALLENGE_SENT:" + t.getPlayerName() + " - Esperando respuesta...");
                },
                () -> sendMessage("ERROR: " + target.getPlayerName() + " ya está en batalla")
            );
    }

    private void processAccept(String challengerName) {
        Optional.of(inBattle.get())
            .filter(battle -> !battle)
            .ifPresentOrElse(
                battle -> findPlayerByName(challengerName)
                    .ifPresentOrElse(
                        this::startBattleWith,
                        () -> sendMessage("ERROR: Jugador '" + challengerName + "' no encontrado")
                    ),
                () -> sendMessage("ERROR: Ya estás en una batalla")
            );
    }

    private void startBattleWith(ClientHandler challenger) {
        Optional.of(challenger)
            .filter(c -> !c.isInBattle())
            .ifPresentOrElse(
                c -> initializeBattle(c),
                () -> sendMessage("ERROR: " + challenger.getPlayerName() + " ya está en otra batalla")
            );
    }

    private void initializeBattle(ClientHandler challenger) {
        challenger.setOpponent(this);
        opponent.set(challenger);
        inBattle.set(true);
        challenger.inBattle.set(true);
        
        // Reiniciar HP para la batalla
        hp.set(100);
        challenger.hp.set(100);
        
        battleStartTime = System.currentTimeMillis();
        
        String battleStartMsg = "BATTLE_START:" + challenger.getPlayerName() + " - ¡Que comience la batalla PVP!";
        sendMessage(battleStartMsg);
        challenger.sendMessage("BATTLE_START:" + playerName.get() + " - ¡Que comience la batalla PVP!");
        
        sendBattleMenu();
        challenger.sendBattleMenu();
    }

    private void setOpponent(ClientHandler opp) {
        opponent.set(opp);
        inBattle.set(true);
    }

    // Menú de armas
    private void showWeaponMenu() {
        String weaponMenu = IntStream.range(0, AVAILABLE_WEAPONS.size())
            .mapToObj(i -> {
                Weapons w = AVAILABLE_WEAPONS.get(i);
                return String.format("%d. %s (Daño: %d)", i + 1, w.getName(), w.getDamage());
            })
            .collect(Collectors.joining("\n", 
                "=== ARMAS DISPONIBLES ===\n", 
                "\n\nEscribe el número o nombre del arma que deseas equipar" +
                "\nO escribe 'BACK' para volver al menú principal"));

        sendMessage(weaponMenu);
        inWeaponMenu.set(true);
    }

    private Runnable processWeaponMenuCommand(String line) {
        boolean isBackCommand = Stream.of("BACK", "B", "0")
            .anyMatch(cmd -> cmd.equalsIgnoreCase(line));

        return isBackCommand 
            ? this::returnToMainMenu
            : findSelectedWeapon(line)
                .map(this::equipWeaponAction)
                .orElse(this::showInvalidWeaponError);
    }

    private Runnable returnToMainMenu() {
        return () -> {
            sendMessage("Volviendo al menú principal...");
            inWeaponMenu.set(false);
            sendMainMenu();
        };
    }

    private Optional<Weapons> findSelectedWeapon(String input) {
        return AVAILABLE_WEAPONS.stream()
            .filter(w -> w.getName().equalsIgnoreCase(input))
            .findFirst()
            .or(() -> parseWeaponIndex(input)
                .filter(index -> index >= 0 && index < AVAILABLE_WEAPONS.size())
                .map(AVAILABLE_WEAPONS::get)
            );
    }

    private Optional<Integer> parseWeaponIndex(String input) {
        try {
            return Optional.of(Integer.parseInt(input) - 1);
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Runnable equipWeaponAction(Weapons selectedWeapon) {
        return () -> {
            weapon.set(selectedWeapon);
            sendMessage("ARMA EQUIPADA: " + selectedWeapon.getName() + 
                " (Daño: " + selectedWeapon.getDamage() + ")");
            inWeaponMenu.set(false);
            sendMainMenu();
        };
    }

    private Runnable showInvalidWeaponError() {
        return () -> {
            sendMessage("ERROR: Arma no válida. Por favor selecciona una opción válida.");
            showWeaponMenu();
        };
    }

    private Optional<Runnable> processWeaponCommand(String line) {
        return Optional.of(line)
            .filter(cmd -> cmd.equalsIgnoreCase("WEAPONS") || cmd.equals("5"))
            .map(cmd -> (Runnable) this::showWeaponMenu)
            .or(() -> Optional.of(line)
                .filter(cmd -> cmd.startsWith("WEAPON:"))
                .map(cmd -> cmd.substring(7).trim())
                .flatMap(this::findWeaponByName)
                .map(this::equipWeaponAction)
            );
    }

    private Optional<Weapons> findWeaponByName(String weaponName) {
        return AVAILABLE_WEAPONS.stream()
            .filter(w -> w.getName().equalsIgnoreCase(weaponName))
            .findFirst();
    }

    private Optional<Runnable> processActionCommand(String line) {
        return Optional.ofNullable(actionCommands.get(line.toUpperCase()));
    }

    private void processAttack() {
        Optional.of(inBattle.get())
            .filter(battle -> !battle)
            .ifPresent(battle -> attackEnemy());
    }

    private void attackEnemy() {
        int damage = Optional.ofNullable(weapon.get())
            .map(Weapons::getDamage)
            .orElse(10);
        
        // Actualizar daño total incluso en PVE
        totalDamage.updateAndGet(current -> current + damage);
        
        sendMessage("YOU_ATTACKED:Enemigo:" + damage);
        
        Optional.of(new Random().nextInt(100))
            .filter(roll -> roll < 30)
            .ifPresent(roll -> {
                sendMessage("ENEMY_DEFEATED:Enemigo");
                // En PVE también cuenta como kill
                kills.updateAndGet(current -> current + 1);
                sendMessage("¡Obtuviste un KILL! Kills totales: " + kills.get());
                updateGlobalStats();
            });
    }

    private void processHeal() {
        // Si está muerto, revivir con 50 HP
        if (!isPlayerAlive()) {
            hp.set(50);
            sendMessage("REVIVED:50");
            sendMessage("HP:" + hp.get());
            sendMainMenu();
        } else {
            // Curar normal si está vivo
            hp.updateAndGet(currentHp -> Math.min(100, currentHp + 15));
            sendMessage("HEALED:15");
            sendMessage("HP:" + hp.get());
        }
    }

    private Optional<Runnable> processInfoCommand(String line) {
        return Optional.ofNullable(infoCommands.get(line.toUpperCase()));
    }

    private void processStatus() {
        String weaponInfo = Optional.ofNullable(weapon.get())
            .map(w -> w.getName() + " (Daño: " + w.getDamage() + ")")
            .orElse("Ninguna equipada (Daño base: 10)");
        
        String battleInfo = Optional.ofNullable(opponent.get())
            .map(opp -> "EN BATALLA PVP contra " + opp.getPlayerName() + " (HP: " + opp.getHp() + ")")
            .orElse("Disponible");
        
        String status = String.format(
            "=== TU ESTADO ===\nHP: %d/100\nARMA: %s\nESTADO: %s\nKills: %d | Muertes: %d | Daño: %d",
            hp.get(), weaponInfo, battleInfo, kills.get(), deaths.get(), totalDamage.get()
        );
        
        sendMessage(status);
    }

    private void processPlayers() {
        String playersList = allPlayers.stream()
            .filter(p -> p != this && p.getPlayerName() != null)
            .map(this::formatPlayerInfo)
            .collect(Collectors.joining("\n"));
        
        String message = Optional.of(playersList)
            .filter(list -> !list.isEmpty())
            .map(list -> "=== JUGADORES CONECTADOS ===\n" + list + "\n=============================")
            .orElse("=== JUGADORES CONECTADOS ===\nNo hay otros jugadores conectados\n=============================");
        
        sendMessage(message);
    }

    private String formatPlayerInfo(ClientHandler player) {
        return String.format("- %s | HP: %d | %s | K/D: %d/%d | Arma: %s",
            player.getPlayerName(),
            player.getHp(),
            player.isInBattle() ? "EN BATALLA" : "DISPONIBLE",
            player.getKills(),
            player.getDeaths(),
            Optional.ofNullable(player.getWeapon()).map(Weapons::getName).orElse("Sin arma")
        );
    }

    private void processResetEnemies() {
        sendMessage("ENEMIES_RESET");
        allPlayers.stream()
            .filter(p -> p != this)
            .forEach(p -> p.sendMessage("ENEMIES_HAVE_BEEN_RESET"));
    }

    private void sendMainMenu() {
        String menu = """
            === COMANDOS DISPONIBLES ===
            1  - ATTACK       - Atacar enemigo PVE
            2  - HEAL         - Curarse 15 HP (o revivir si estás muerto)
            3  - STATUS       - Ver tu estado
            4  - PLAYERS      - Listar jugadores
            5  - WEAPONS      - Menú de armas
            6  - CHALLENGE:nombre - Desafiar a jugador
            7  - ACCEPT:nombre    - Aceptar desafío
            8  - RESET_ENEMIES    - Reiniciar enemigos
            9  - HELP         - Mostrar ayuda
            STATS            - Ver tus estadísticas
            LEADERBOARD      - Ver ranking global
            0  - EXIT         - Salir
            =============================
            """;
        sendMessage(menu);
    }

    public void sendMessage(String msg) {
        Optional.ofNullable(out).ifPresent(o -> o.println(msg));
    }

    private void cleanup() {
        try {
            Optional.ofNullable(socket)
                .filter(s -> !s.isClosed())
                .ifPresent(s -> {
                    try { s.close(); } catch (IOException ignored) {}
                });
        } catch (Exception ignored) {}
        
        allPlayers.remove(this);
        
        // Actualizar estadísticas globales antes de desconectar
        updateGlobalStats();
        
        Optional.ofNullable(opponent.get())
            .ifPresent(opp -> {
                opp.sendMessage("TU_OPONENTE_SE_DESCONECTO");
                opp.endBattle();
            });
        
        System.out.println("Jugador " + playerName.get() + " desconectado. Stats: " +
            kills.get() + " kills, " + deaths.get() + " deaths, " + totalDamage.get() + " damage");
    }

    // Métodos auxiliares para verificar estado
    public boolean isPlayerAlive() {
        return hp.get() > 0;
    }

    // Getters para estadísticas
    public int getKills() { return kills.get(); }
    public int getDeaths() { return deaths.get(); }
    public int getTotalDamage() { return totalDamage.get(); }

    public String getPlayerName() { return playerName.get(); }
    public int getHp() { return hp.get(); }
    public Weapons getWeapon() { return weapon.get(); }
    public boolean isInBattle() { return inBattle.get(); }
}

// Clase para manejar estadísticas de jugador
class PlayerStats {
    private final String playerName;
    private int kills;
    private int deaths;
    private int totalDamage;
    
    public PlayerStats(String playerName) {
        this.playerName = playerName;
        this.kills = 0;
        this.deaths = 0;
        this.totalDamage = 0;
    }
    
    public void updateStats(int newKills, int newDeaths, int newDamage) {
        this.kills = newKills;
        this.deaths = newDeaths;
        this.totalDamage = newDamage;
    }
    
    public double getKDRatio() {
        return deaths > 0 ? (double) kills / deaths : kills;
    }
    
    public String getPlayerName() { return playerName; }
    public int getKills() { return kills; }
    public int getDeaths() { return deaths; }
    public int getTotalDamage() { return totalDamage; }
    
    @Override
    public String toString() {
        return String.format("%-15s | K: %-3d | D: %-3d | K/D: %-5.2f | Daño: %-6d",
            playerName, kills, deaths, getKDRatio(), totalDamage);
    }
}