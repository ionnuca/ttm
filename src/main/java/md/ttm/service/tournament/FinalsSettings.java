package md.ttm.service.tournament;

import java.util.List;
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

    /**
     * Cel mai mare număr de jucători din fiecare grupă în fiecare finală (în afară de ultima), pentru grupele date:
     * fiecare grupă trebuie să aibă destui jucători pentru finalele dinainte de ultima, iar ultima finală – cei rămași
     * din toate grupele – cel puțin 2 jucători. O grupă poate să nu mai aibă pe nimeni în ultima finală.
     * Rezultatul e 0 dacă nu se pot forma atâtea finale.
     */
    public static int maxQualifiers(int finalCount, List<Integer> groupSizes) {
        if (finalCount < 2 || groupSizes.isEmpty()) {
            return 0;
        }
        int smallest = groupSizes.stream().mapToInt(Integer::intValue).min().orElse(0);
        for (int q = smallest / (finalCount - 1); q >= minQualifiers(groupSizes.size()); q--) {
            int taken = q * (finalCount - 1);
            int last = groupSizes.stream().mapToInt(n -> Math.max(0, n - taken)).sum();
            if (last >= 2) {
                return q;
            }
        }
        return 0;
    }

    /** Cel mai mic număr de jucători pe grupă într-o finală, ca finala să aibă cel puțin 2 jucători. */
    public static int minQualifiers(int groupCount) {
        return groupCount >= 2 ? 1 : 2;
    }
}
