// StatsProcessor.java - Procesador de estadísticas con programación funcional
import java.util.*;
import java.util.stream.*;

public class StatsProcessor {
    private final List<MatchResult> matchHistory;

    public StatsProcessor() {
        this.matchHistory = new ArrayList<>();
    }

    public StatsProcessor(List<MatchResult> initialResults) {
        this.matchHistory = new ArrayList<>(initialResults);
    }

    // Agregar resultado de batalla
    public void addMatchResult(MatchResult result) {
        matchHistory.add(result);
        System.out.println("Resultado registrado: " + result);
    }

    // === ANÁLISIS FUNCIONALES ===

    // 1. Top N jugadores por daño total
    public void showTopPlayersByDamage(int topN) {
        System.out.println("\n=== TOP " + topN + " JUGADORES POR DAÑO TOTAL ===");
        
        getTotalDamageByPlayer().entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
            .limit(topN)
            .forEach(entry -> System.out.printf("%-15s: %d puntos de daño\n", 
                entry.getKey(), entry.getValue()));
    }

    // 2. Promedio de duración de batallas
    public void showAverageDuration() {
        double avgMs = matchHistory.stream()
            .collect(Collectors.averagingLong(MatchResult::getDurationMs));
        
        double avgSeconds = avgMs / 1000.0;
        System.out.printf("\nDuración promedio de batallas: %.2f segundos\n", avgSeconds);
    }

    // 3. Jugadores con daño promedio superior a un umbral
    public void showPlayersAboveAverageDamage(int threshold) {
        System.out.println("\n=== JUGADORES CON DAÑO PROMEDIO > " + threshold + " ===");
        
        getAverageDamageByPlayer().entrySet().stream()
            .filter(entry -> entry.getValue() > threshold)
            .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
            .forEach(entry -> System.out.printf("%-15s: %.1f de daño promedio\n", 
                entry.getKey(), entry.getValue()));
    }

    // 4. Conteo de victorias por jugador (usando parallelStream)
    public void showVictoriesCount() {
        System.out.println("\n=== VICTORIAS POR JUGADOR ===");
        
        Map<String, Long> victories = matchHistory.parallelStream()
            .collect(Collectors.groupingBy(
                MatchResult::getWinner, 
                Collectors.counting()
            ));
        
        victories.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .forEach(entry -> System.out.printf("%-15s: %d victorias\n", 
                entry.getKey(), entry.getValue()));
    }

    // 5. Arma más efectiva (mayor daño promedio)
    public void showMostEffectiveWeapon() {
        System.out.println("\n=== ARMA MÁS EFECTIVA ===");
        
        matchHistory.stream()
            .collect(Collectors.groupingBy(
                MatchResult::getWinnerWeapon,
                Collectors.averagingInt(MatchResult::getWinnerDamageDealt)
            ))
            .entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .ifPresentOrElse(
                entry -> System.out.printf("%s con %.1f de daño promedio\n", 
                    entry.getKey(), entry.getValue()),
                () -> System.out.println("No hay datos de armas")
            );
    }

    // 6. Tasa de rendiciones
    public void showSurrenderRate() {
        long totalMatches = matchHistory.size();
        long surrenders = matchHistory.stream()
            .filter(MatchResult::wasSurrender)
            .count();
        
        double rate = totalMatches > 0 ? (surrenders * 100.0 / totalMatches) : 0;
        System.out.printf("\nTasa de rendiciones: %.1f%% (%d de %d batallas)\n", 
            rate, surrenders, totalMatches);
    }

    // 7. Batalla más larga y más corta
    public void showDurationExtremes() {
        System.out.println("\n=== BATALLAS MÁS LARGA Y MÁS CORTA ===");
        
        matchHistory.stream()
            .max(Comparator.comparingLong(MatchResult::getDurationMs))
            .ifPresent(longest -> System.out.printf("Más larga: %.1fs - %s vs %s\n",
                longest.getDurationSeconds(), longest.getWinner(), longest.getLoser()));
        
        matchHistory.stream()
            .min(Comparator.comparingLong(MatchResult::getDurationMs))
            .ifPresent(shortest -> System.out.printf("Más corta: %.1fs - %s vs %s\n",
                shortest.getDurationSeconds(), shortest.getWinner(), shortest.getLoser()));
    }

    // 8. Análisis de rivalidades (enfrentamientos entre mismos jugadores)
    public void showRivalries() {
        System.out.println("\n=== RIVALIDADES (ENFRENTAMIENTOS REPETIDOS) ===");
        
        matchHistory.stream()
            .collect(Collectors.groupingBy(
                match -> createRivalryKey(match.getWinner(), match.getLoser()),
                Collectors.counting()
            ))
            .entrySet().stream()
            .filter(entry -> entry.getValue() > 1)
            .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
            .forEach(entry -> System.out.printf("%s: %d batallas\n", 
                entry.getKey(), entry.getValue()));
    }

    // === MÉTODOS AUXILIARES ===

    private Map<String, Integer> getTotalDamageByPlayer() {
        // Suma el daño como ganador y como perdedor
        Map<String, Integer> damageMap = new HashMap<>();
        
        matchHistory.forEach(match -> {
            damageMap.merge(match.getWinner(), match.getWinnerDamageDealt(), Integer::sum);
            damageMap.merge(match.getLoser(), match.getLoserDamageDealt(), Integer::sum);
        });
        
        return damageMap;
    }

    private Map<String, Double> getAverageDamageByPlayer() {
        // Calcula el promedio de daño por jugador (como ganador y perdedor)
        Map<String, List<Integer>> damageByPlayer = new HashMap<>();
        
        matchHistory.forEach(match -> {
            damageByPlayer.computeIfAbsent(match.getWinner(), k -> new ArrayList<>())
                .add(match.getWinnerDamageDealt());
            damageByPlayer.computeIfAbsent(match.getLoser(), k -> new ArrayList<>())
                .add(match.getLoserDamageDealt());
        });
        
        return damageByPlayer.entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue().stream()
                    .mapToInt(Integer::intValue)
                    .average()
                    .orElse(0.0)
            ));
    }

    private String createRivalryKey(String player1, String player2) {
        // Crea clave ordenada para identificar rivalidad independiente del orden
        return player1.compareTo(player2) < 0 
            ? player1 + " vs " + player2 
            : player2 + " vs " + player1;
    }

    // Reporte completo
    public void generateFullReport() {
        System.out.println("\n" + "=".repeat(60));
        System.out.println("REPORTE COMPLETO DE ESTADÍSTICAS DEL SERVIDOR");
        System.out.println("=".repeat(60));
        System.out.println("Total de batallas: " + matchHistory.size());
        
        showTopPlayersByDamage(3);
        showVictoriesCount();
        showAverageDuration();
        showPlayersAboveAverageDamage(100);
        showMostEffectiveWeapon();
        showSurrenderRate();
        showDurationExtremes();
        showRivalries();
        
        System.out.println("\n" + "=".repeat(60));
    }

}
