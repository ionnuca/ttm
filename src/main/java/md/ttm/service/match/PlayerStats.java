package md.ttm.service.match;

/**
 * Statistica unui jucător din toate meciurile cu rezultat (inclusiv din turneele în desfășurare).
 *
 * @param wins           victorii jucate (fără cele tehnice)
 * @param losses         înfrângeri jucate (fără cele tehnice)
 * @param walkoverWins   victorii tehnice (adversarul nu s-a prezentat)
 * @param walkoverLosses înfrângeri tehnice
 * @param tournaments    turnee începute la care a participat
 * @param bestRating     cel mai mare rating atins (inclusiv cel inițial)
 */
public record PlayerStats(int wins, int losses, int walkoverWins, int walkoverLosses,
                          int setsWon, int setsLost, int tournaments, int bestRating) {

    public int played() {
        return wins + losses;
    }

    /** Procentul de victorii din meciurile jucate efectiv; {@code null} dacă nu a jucat încă. */
    public Integer winPercent() {
        return played() == 0 ? null : Math.round(100f * wins / played());
    }
}
