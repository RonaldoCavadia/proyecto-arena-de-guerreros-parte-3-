// MatchResult.java - Modelo inmutable para almacenar resultados de batallas PVP
import java.time.LocalDateTime;
import java.util.Objects;

public final class MatchResult {
    private final String winner;
    private final String loser;
    private final int winnerDamageDealt;
    private final int loserDamageDealt;
    private final long durationMs;
    private final LocalDateTime timestamp;
    private final String winnerWeapon;
    private final String loserWeapon;
    private final boolean wasSurrender;

    public MatchResult(String winner, String loser, 
                      int winnerDamageDealt, int loserDamageDealt,
                      long durationMs, String winnerWeapon, 
                      String loserWeapon, boolean wasSurrender) {
        this.winner = winner;
        this.loser = loser;
        this.winnerDamageDealt = winnerDamageDealt;
        this.loserDamageDealt = loserDamageDealt;
        this.durationMs = durationMs;
        this.timestamp = LocalDateTime.now();
        this.winnerWeapon = winnerWeapon;
        this.loserWeapon = loserWeapon;
        this.wasSurrender = wasSurrender;
    }

    // Getters
    public String getWinner() { return winner; }
    public String getLoser() { return loser; }
    public int getWinnerDamageDealt() { return winnerDamageDealt; }
    public int getLoserDamageDealt() { return loserDamageDealt; }
    public long getDurationMs() { return durationMs; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getWinnerWeapon() { return winnerWeapon; }
    public String getLoserWeapon() { return loserWeapon; }
    public boolean wasSurrender() { return wasSurrender; }

    // Método para obtener duración en segundos
    public double getDurationSeconds() {
        return durationMs / 1000.0;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        MatchResult that = (MatchResult) obj;
        return winnerDamageDealt == that.winnerDamageDealt &&
               loserDamageDealt == that.loserDamageDealt &&
               durationMs == that.durationMs &&
               wasSurrender == that.wasSurrender &&
               Objects.equals(winner, that.winner) &&
               Objects.equals(loser, that.loser) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(winnerWeapon, that.winnerWeapon) &&
               Objects.equals(loserWeapon, that.loserWeapon);
    }

    @Override
    public int hashCode() {
        return Objects.hash(winner, loser, winnerDamageDealt, loserDamageDealt, 
                          durationMs, timestamp, winnerWeapon, loserWeapon, wasSurrender);
    }

    @Override
    public String toString() {
        return String.format("Batalla: %s (%s) venció a %s (%s) | Daño: %d vs %d | Duración: %.1fs %s",
            winner, winnerWeapon, loser, loserWeapon,
            winnerDamageDealt, loserDamageDealt, getDurationSeconds(),
            wasSurrender ? "[RENDICIÓN]" : "");
    }
}