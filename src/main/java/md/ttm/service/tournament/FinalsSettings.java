package md.ttm.service.tournament;

import java.util.Set;

/**
 * Setările etapei 2 la „Grupe + finale”.
 *
 * @param finalCount  câte finale se joacă (cel puțin 2)
 * @param qualifiers  câți jucători din fiecare grupă trec în fiecare finală, în afară de ultima: Finala k primește
 *                    locurile (k-1)·q+1 … k·q din fiecare grupă, iar ultima finală pe toți cei rămași
 * @param feeRefunds  finalele (2 … finalCount) al căror câștigător primește cât taxa de participare
 *                    (doar la turneele comerciale)
 */
public record FinalsSettings(int finalCount, int qualifiers, Set<Integer> feeRefunds) {

    public FinalsSettings {
        feeRefunds = feeRefunds == null ? Set.of() : Set.copyOf(feeRefunds);
    }

    /** Două finale: primii {@code qualifiers} din fiecare grupă în Finala 1, ceilalți în Finala 2 (care returnează taxa). */
    public static FinalsSettings twoFinals(int qualifiers) {
        return new FinalsSettings(2, qualifiers, Set.of(2));
    }

    /** Finala (1 … finalCount) în care intră jucătorul de pe locul dat al grupei. */
    public int finalFor(int place) {
        return Math.min(finalCount, (Math.max(place, 1) - 1) / qualifiers + 1);
    }

    /** Cel mai mare număr de calificați pe finală pentru grupe de cel puțin {@code smallestGroup} jucători. */
    public static int maxQualifiers(int finalCount, int smallestGroup) {
        return (smallestGroup - 1) / (finalCount - 1);
    }
}
