package md.ttm.service.rating;

/**
 * Formulele ratingului Elo, așa cum sunt folosite în aplicație.
 * <pre>
 *   E_A = 1 / (1 + 10^((R_B − R_A) / 400))      probabilitatea așteptată ca A să câștige
 *   R_A' = R_A + K × (S_A − E_A)               S_A = 1 victorie, 0 înfrângere
 * </pre>
 * <ul>
 *     <li>K = 40 până la 30 de meciuri cu rating jucate, apoi K = 20 (ca la FIDE);</li>
 *     <li>o diferență de rating mai mare de 400 se socotește ca 400;</li>
 *     <li>schimbarea pe un turneu se rotunjește o singură dată, la număr întreg
 *     (0,5 se rotunjește departe de zero).</li>
 * </ul>
 */
public final class Elo {

    public static final double DIVISOR = 400.0;
    public static final int MAX_DIFFERENCE = 400;
    public static final int K_PROVISIONAL = 40;
    public static final int K_ESTABLISHED = 20;
    public static final int PROVISIONAL_MATCHES = 30;

    private Elo() {
    }

    /** Probabilitatea ca jucătorul cu {@code rating} să-l învingă pe cel cu {@code opponentRating}. */
    public static double expectedScore(int rating, int opponentRating) {
        int difference = Math.max(-MAX_DIFFERENCE, Math.min(MAX_DIFFERENCE, opponentRating - rating));
        return 1.0 / (1.0 + Math.pow(10, difference / DIVISOR));
    }

    /** Factorul K în funcție de câte meciuri cu rating a jucat deja jucătorul. */
    public static int kFactor(int ratedMatchesSoFar) {
        return ratedMatchesSoFar < PROVISIONAL_MATCHES ? K_PROVISIONAL : K_ESTABLISHED;
    }

    /** Schimbarea (nerotunjită) pentru un meci. */
    public static double change(int rating, int opponentRating, boolean won, int k) {
        return k * ((won ? 1.0 : 0.0) - expectedScore(rating, opponentRating));
    }

    /** Rotunjire la întreg, cu 0,5 departe de zero (ca în regulamentul FIDE). */
    public static int round(double value) {
        return (int) Math.signum(value) * (int) Math.round(Math.abs(value));
    }
}
