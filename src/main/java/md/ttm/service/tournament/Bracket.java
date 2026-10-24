package md.ttm.service.tournament;

import md.ttm.model.tournament.KnockoutType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tabloul eliminatoriu, calculat din lista jucătorilor (ordinea de calificare) și rezultatele meciurilor jucate.
 * Nu depinde de baza de date: aceleași date dau mereu același tablou.
 * <p>
 * Tabloul are o mărime putere a lui 2; locurile libere sunt „fără adversar” (jucătorul trece direct mai departe),
 * dați întâi celor mai bine clasați. Așezarea e cea standard: 1 – ultimul, 2 – penultimul …, primii doi se pot
 * întâlni abia în finală.
 * <ul>
 *     <li><b>Olimpic</b> ({@link KnockoutType#SINGLE}): cine pierde iese. Finala decide locurile 1 și 2;
 *     cei învinși în semifinale împart locul 3, cei din sferturi locul 5 ș.a.m.d.</li>
 *     <li><b>Cu ramura de jos</b> ({@link KnockoutType#DOUBLE}): cine pierde în tabloul principal trece în ramura
 *     de jos; cine pierde în ramura de jos iese. Câștigătorul ramurii de jos joacă marea finală cu câștigătorul
 *     tabloului principal; dacă o câștigă, se joacă un meci decisiv (fiecare a pierdut o dată).
 *     Locul 3 e cel care pierde finala ramurii de jos.</li>
 * </ul>
 * Cheile meciurilor: {@code W<tur>-<nr>} (tabloul principal), {@code L<tur>-<nr>} (ramura de jos),
 * {@code GF} (marea finală), {@code GF2} (meciul decisiv).
 */
public final class Bracket {

    /** Locul liber („fără adversar”). */
    public static final long BYE = -1L;

    public enum Section { UPPER, LOWER, FINAL }

    /**
     * Un meci din tablou.
     *
     * @param a, b      jucătorii; {@code null} = încă necunoscut, {@link #BYE} = loc liber
     * @param winner    câștigătorul (sau cel care trece direct); {@code null} dacă nu se știe încă
     * @param loser     învinsul; {@link #BYE} dacă meciul nu s-a jucat (trecere directă)
     * @param playable  ambii jucători sunt cunoscuți: meciul se joacă (are sau va avea un rezultat)
     * @param rounds    numărul de tururi din secțiunea meciului (pentru denumiri)
     */
    public record Match(String key, Section section, int round, int index, int rounds,
                        Long a, Long b, Long winner, Long loser, boolean playable) {

        public boolean decided() {
            return winner != null;
        }
    }

    /** Locul unui jucător; la egalitate (eliminați în același tur), mai mulți au același loc. */
    public record Placing(long participantId, int place) {
    }

    /**
     * @param matches  toate meciurile tabloului, în ordinea în care se joacă
     * @param placings locurile stabilite până acum, de la primul
     * @param complete câștigătorul turneului e stabilit
     */
    public record State(KnockoutType type, int size, List<Match> matches, List<Placing> placings, boolean complete) {

        public Match match(String key) {
            return matches.stream().filter(m -> m.key().equals(key)).findFirst().orElse(null);
        }
    }

    private Bracket() {
    }

    /**
     * @param seeds   jucătorii, în ordinea de calificare (primul = cel mai bine clasat), minimum 2
     * @param winners câștigătorul fiecărui meci jucat, după cheie
     */
    public static State compute(KnockoutType type, List<Long> seeds, Map<String, Long> winners) {
        if (seeds.size() < 2) {
            throw new IllegalArgumentException("Tabloul are nevoie de cel puțin 2 jucători");
        }
        int size = 1;
        while (size < seeds.size()) {
            size *= 2;
        }
        int k = Integer.numberOfTrailingZeros(size);
        Builder b = new Builder(winners);

        // tabloul principal
        int[] positions = positions(size);
        Match[][] upper = new Match[k + 1][];
        upper[1] = new Match[size / 2];
        for (int i = 1; i <= size / 2; i++) {
            Long x = seed(seeds, positions[2 * i - 2]);
            Long y = seed(seeds, positions[2 * i - 1]);
            upper[1][i - 1] = b.add("W1-" + i, Section.UPPER, 1, i, k, x, y);
        }
        for (int r = 2; r <= k; r++) {
            upper[r] = new Match[size >> r];
            for (int i = 1; i <= upper[r].length; i++) {
                upper[r][i - 1] = b.add("W" + r + "-" + i, Section.UPPER, r, i, k,
                        upper[r - 1][2 * i - 2].winner(), upper[r - 1][2 * i - 1].winner());
            }
        }
        Match upperFinal = upper[k][0];

        List<List<Long>> eliminated = new ArrayList<>(); // de la primii eliminați spre ultimii
        Long champion;
        Long runnerUp;
        if (type == KnockoutType.SINGLE) {
            for (int r = 1; r < k; r++) {
                eliminated.add(losers(upper[r]));
            }
            champion = upperFinal.winner();
            runnerUp = upperFinal.decided() ? upperFinal.loser() : null;
        } else {
            Long lowerChampion;
            if (k == 1) {
                lowerChampion = upperFinal.decided() ? upperFinal.loser() : null;
            } else {
                int lowerRounds = 2 * k - 2;
                Match[] previous = new Match[size / 4];
                for (int j = 1; j <= size / 4; j++) {
                    previous[j - 1] = b.add("L1-" + j, Section.LOWER, 1, j, lowerRounds,
                            upper[1][2 * j - 2].loser(), upper[1][2 * j - 1].loser());
                }
                eliminated.add(losers(previous));
                for (int t = 1; t <= k - 1; t++) {
                    // cei învinși în tabloul principal (turul t+1) intră în ramura de jos, în ordine inversă
                    // la fiecare al doilea tur, ca să nu se reîntâlnească prea repede
                    Match[] incoming = upper[t + 1];
                    Match[] even = new Match[previous.length];
                    for (int j = 1; j <= even.length; j++) {
                        Match from = t % 2 == 1 ? incoming[incoming.length - j] : incoming[j - 1];
                        even[j - 1] = b.add("L" + (2 * t) + "-" + j, Section.LOWER, 2 * t, j, lowerRounds,
                                previous[j - 1].winner(), from.loser());
                    }
                    eliminated.add(losers(even));
                    previous = even;
                    if (t < k - 1) {
                        Match[] odd = new Match[even.length / 2];
                        for (int j = 1; j <= odd.length; j++) {
                            odd[j - 1] = b.add("L" + (2 * t + 1) + "-" + j, Section.LOWER, 2 * t + 1, j, lowerRounds,
                                    even[2 * j - 2].winner(), even[2 * j - 1].winner());
                        }
                        eliminated.add(losers(odd));
                        previous = odd;
                    }
                }
                lowerChampion = previous[0].winner();
            }
            Match grandFinal = b.add("GF", Section.FINAL, 1, 1, 2, upperFinal.winner(), lowerChampion);
            if (grandFinal.decided() && grandFinal.winner().equals(grandFinal.b())
                    && grandFinal.b() != BYE && grandFinal.a() != BYE) {
                // câștigătorul ramurii de jos a câștigat: ambii au câte o înfrângere, se joacă meciul decisiv
                Match decider = b.add("GF2", Section.FINAL, 2, 1, 2, grandFinal.a(), grandFinal.b());
                champion = decider.winner();
                runnerUp = decider.decided() ? decider.loser() : null;
            } else {
                champion = grandFinal.winner();
                runnerUp = grandFinal.decided() ? grandFinal.loser() : null;
            }
        }

        List<Placing> placings = new ArrayList<>();
        if (champion != null) {
            // turneul s-a încheiat: primul, al doilea, apoi cei eliminați, de la ultimul tur spre primul
            List<List<Long>> tiers = new ArrayList<>();
            tiers.add(List.of(champion));
            tiers.add(List.of(runnerUp));
            List<List<Long>> fromLast = new ArrayList<>(eliminated);
            Collections.reverse(fromLast);
            tiers.addAll(fromLast);
            int place = 1;
            for (List<Long> tier : tiers) {
                List<Long> real = real(tier);
                for (Long id : real) {
                    placings.add(new Placing(id, place));
                }
                place += real.size();
            }
        } else {
            // în desfășurare: se știu locurile celor deja eliminați, numărate de la coadă
            int remaining = seeds.size();
            List<Placing> fromBottom = new ArrayList<>();
            for (List<Long> tier : eliminated) {
                if (tier.contains(null)) {
                    break;
                }
                List<Long> real = real(tier);
                int tierPlace = remaining - real.size() + 1;
                for (Long id : real) {
                    fromBottom.add(new Placing(id, tierPlace));
                }
                remaining -= real.size();
            }
            Collections.reverse(fromBottom);
            placings.addAll(fromBottom);
        }
        return new State(type, size, b.matches, placings, champion != null);
    }

    /** Așezarea standard a capilor de serie: pentru 8, [1, 8, 4, 5, 2, 7, 3, 6]. */
    static int[] positions(int size) {
        List<Integer> order = new ArrayList<>(List.of(1, 2));
        while (order.size() < size) {
            int next = order.size() * 2 + 1;
            List<Integer> expanded = new ArrayList<>();
            for (int s : order) {
                expanded.add(s);
                expanded.add(next - s);
            }
            order = expanded;
        }
        return order.stream().mapToInt(Integer::intValue).toArray();
    }

    private static Long seed(List<Long> seeds, int position) {
        return position <= seeds.size() ? seeds.get(position - 1) : BYE;
    }

    private static List<Long> real(List<Long> tier) {
        return tier.stream().filter(id -> id != null && id != BYE).toList();
    }

    private static List<Long> losers(Match[] matches) {
        List<Long> result = new ArrayList<>();
        for (Match m : matches) {
            result.add(m.decided() ? m.loser() : null);
        }
        return result;
    }

    private static final class Builder {
        private final Map<String, Long> winners;
        private final List<Match> matches = new ArrayList<>();

        Builder(Map<String, Long> winners) {
            this.winners = winners;
        }

        Match add(String key, Section section, int round, int index, int rounds, Long a, Long b) {
            Long winner = null;
            Long loser = null;
            boolean playable = false;
            if (a != null && b != null) {
                if (a == BYE && b == BYE) {
                    winner = BYE;
                    loser = BYE;
                } else if (a == BYE) {
                    winner = b;
                    loser = BYE;
                } else if (b == BYE) {
                    winner = a;
                    loser = BYE;
                } else {
                    playable = true;
                    Long w = winners.get(key);
                    if (a.equals(w) || b.equals(w)) {
                        winner = w;
                        loser = a.equals(w) ? b : a;
                    }
                }
            }
            Match match = new Match(key, section, round, index, rounds, a, b, winner, loser, playable);
            matches.add(match);
            return match;
        }
    }

    /** Cheile meciurilor care se joacă, cu jucătorii lor (pentru sincronizarea cu baza de date). */
    public static Map<String, long[]> playable(State state) {
        Map<String, long[]> result = new LinkedHashMap<>();
        for (Match m : state.matches()) {
            if (m.playable()) {
                result.put(m.key(), new long[]{m.a(), m.b()});
            }
        }
        return result;
    }
}
