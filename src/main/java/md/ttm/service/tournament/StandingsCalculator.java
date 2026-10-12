package md.ttm.service.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Calculează tabelul unui turneu Round Robin: rezultatul fiecărei întâlniri, seturile,
 * punctele și locul fiecărui jucător.
 * <p>
 * Puncte: 2 pentru victorie, 1 pentru înfrângere, 0 pentru înfrângere tehnică (W).
 * O victorie tehnică se socotește la seturi ca victorie la scor alb (ex. 3:0 la „best of 5”).
 * <p>
 * Departajarea la egalitate de puncte ține cont doar de meciurile directe dintre jucătorii aflați la egalitate:
 * <ol>
 *     <li>punctele din aceste meciuri;</li>
 *     <li>raportul seturilor câștigate / pierdute din aceste meciuri.</li>
 * </ol>
 * Dacă un criteriu desparte doar o parte dintre jucători, pentru cei rămași la egalitate calculul se reia
 * de la punctul 1, doar cu meciurile dintre ei (ca la ITTF). Cei care nu pot fi despărțiți împart locul
 * (de exemplu 1, 1, 1, 4).
 * <p>
 * La turneele comerciale, după ce s-au jucat toate meciurile tabelului, jucătorii rămași la egalitate joacă
 * între ei câte un set de departajare, cu scorul la puncte. Contează, tot între ei, setele câștigate, apoi
 * raportul punctelor câștigate / pierdute; dacă egalitatea rămâne, se joacă o nouă rundă.
 */
public final class StandingsCalculator {

    public static final int POINTS_WIN = 2;
    public static final int POINTS_LOSS = 1;
    public static final int POINTS_WALKOVER_LOSS = 0;

    /** Un jucător din grupă; {@code seed} = poziția în grupă (1 = primul). */
    public record Competitor(long id, int seed) {
    }

    /** Un meci jucat. La victorie tehnică, seturile se ignoră. */
    public record Result(long a, long b, int setsA, int setsB, long winner, boolean walkover) {
    }

    /** Un set de departajare jucat, cu scorul la puncte. */
    public record Tiebreak(int round, long a, long b, int pointsA, int pointsB) {
    }

    /** Un set de departajare care trebuie jucat. */
    public record Pairing(int round, long a, long b) {
    }

    /**
     * Celula din tabel, din perspectiva jucătorului de pe rând.
     *
     * @param points punctele obținute (2, 1 sau 0)
     * @param score  scorul la seturi („3:1”), „W” pentru victorie tehnică, „L” pentru înfrângere tehnică
     */
    public record Cell(int points, String score, boolean won, boolean walkover) {
    }

    /**
     * Un rând din tabel.
     *
     * @param place       locul în clasament; 0 dacă încă nu s-a jucat niciun meci în turneu
     * @param sharedPlace locul e împărțit cu alți jucători (egalitate nedepartajată)
     */
    public record Row(long id, int seed, Map<Long, Cell> cells, int played, int setsWon, int setsLost,
                      int points, int place, boolean sharedPlace) {
    }

    /**
     * Tabelul complet.
     *
     * @param rows      rândurile, în ordinea din grupă
     * @param tiebreaks seturile de departajare care trebuie jucate acum (gol dacă nu e nevoie)
     */
    public record Table(List<Row> rows, List<Pairing> tiebreaks) {
    }

    private StandingsCalculator() {
    }

    /** Tabelul fără seturi de departajare (egalitățile nedepartajate împart locul). */
    public static List<Row> compute(List<Competitor> competitors, List<Result> results, int setsToWin) {
        return table(competitors, results, setsToWin, false, List.of()).rows();
    }

    /**
     * @param competitors      jucătorii, în ordinea din grupă
     * @param results          meciurile jucate
     * @param setsToWin        seturile necesare pentru a câștiga un meci
     * @param tiebreaksAllowed egalitățile nedepartajate se decid prin seturi de departajare
     *                         (turneu comercial, toate meciurile tabelului jucate)
     * @param tiebreaks        seturile de departajare deja jucate
     */
    public static Table table(List<Competitor> competitors, List<Result> results, int setsToWin,
                              boolean tiebreaksAllowed, List<Tiebreak> tiebreaks) {
        Map<Long, Stats> stats = new LinkedHashMap<>();
        for (Competitor c : competitors) {
            stats.put(c.id(), new Stats(c));
        }
        List<Result> counted = new ArrayList<>();
        for (Result r : results) {
            Stats a = stats.get(r.a());
            Stats b = stats.get(r.b());
            if (a == null || b == null) {
                continue;
            }
            counted.add(r);
            boolean aWon = r.winner() == r.a();
            int setsA = setsFor(r, true, setsToWin);
            int setsB = setsFor(r, false, setsToWin);
            a.add(r.b(), aWon, r.walkover(), setsA, setsB);
            b.add(r.a(), !aWon, r.walkover(), setsB, setsA);
        }

        Map<Long, Integer> places = new HashMap<>();
        Set<Long> shared = new HashSet<>();
        List<Pairing> pending = new ArrayList<>();
        if (!counted.isEmpty()) {
            List<List<Stats>> tiers = new ArrayList<>();
            for (List<Stats> level : partition(new ArrayList<>(stats.values()), s -> (double) s.points)) {
                tiers.addAll(resolve(level, directMatches(counted, setsToWin)));
            }
            if (tiebreaksAllowed) {
                List<List<Stats>> decided = new ArrayList<>();
                for (List<Stats> tier : tiers) {
                    decided.addAll(tier.size() > 1 ? resolveByTiebreaks(tier, 1, tiebreaks, pending) : List.of(tier));
                }
                tiers = decided;
            }
            int place = 1;
            for (List<Stats> tier : tiers) {
                for (Stats s : tier) {
                    places.put(s.competitor.id(), place);
                    if (tier.size() > 1) {
                        shared.add(s.competitor.id());
                    }
                }
                place += tier.size();
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Stats s : stats.values()) {
            long id = s.competitor.id();
            rows.add(new Row(id, s.competitor.seed(), Map.copyOf(s.cells), s.played,
                    s.setsWon, s.setsLost, s.points, places.getOrDefault(id, 0), shared.contains(id)));
        }
        return new Table(rows, pending);
    }

    /** Valorile după care se compară jucătorii unui grup: întâi primul criteriu, apoi al doilea. */
    private interface Criteria extends Function<Set<Long>, Map<Long, double[]>> {
    }

    /**
     * Ordonează jucătorii aflați la egalitate. Întoarce nivelurile, de la cel mai bun; un nivel cu mai mulți
     * jucători înseamnă egalitate nedepartajată.
     */
    private static List<List<Stats>> resolve(List<Stats> group, Criteria criteria) {
        if (group.size() <= 1) {
            return List.of(group);
        }
        Set<Long> ids = ids(group);
        Map<Long, double[]> values = criteria.apply(ids);
        for (int criterion = 0; criterion < 2; criterion++) {
            int index = criterion;
            List<List<Stats>> parts = partition(group, s -> values.get(s.competitor.id())[index]);
            if (parts.size() > 1) {
                List<List<Stats>> result = new ArrayList<>();
                // pentru cei rămași la egalitate, calculul se reia doar cu meciurile dintre ei
                parts.forEach(part -> result.addAll(resolve(part, criteria)));
                return result;
            }
        }
        return List.of(group);
    }

    /** Meciurile directe: punctele, apoi raportul seturilor, doar din meciurile dintre jucătorii dați. */
    private static Criteria directMatches(List<Result> results, int setsToWin) {
        return ids -> {
            Map<Long, int[]> sums = new HashMap<>(); // [puncte, seturi câștigate, seturi pierdute]
            ids.forEach(id -> sums.put(id, new int[3]));
            for (Result r : results) {
                if (!ids.contains(r.a()) || !ids.contains(r.b())) {
                    continue;
                }
                boolean aWon = r.winner() == r.a();
                int setsA = setsFor(r, true, setsToWin);
                int setsB = setsFor(r, false, setsToWin);
                int[] a = sums.get(r.a());
                int[] b = sums.get(r.b());
                a[0] += points(aWon, r.walkover());
                b[0] += points(!aWon, r.walkover());
                a[1] += setsA;
                a[2] += setsB;
                b[1] += setsB;
                b[2] += setsA;
            }
            Map<Long, double[]> values = new HashMap<>();
            sums.forEach((id, v) -> values.put(id, new double[]{v[0], ratio(v[1], v[2])}));
            return values;
        };
    }

    /** Seturile de departajare dintr-o rundă: setele câștigate, apoi raportul punctelor. */
    private static Criteria tiebreakRound(List<Tiebreak> tiebreaks, int round) {
        return ids -> {
            Map<Long, int[]> sums = new HashMap<>(); // [seturi câștigate, puncte câștigate, puncte pierdute]
            ids.forEach(id -> sums.put(id, new int[3]));
            for (Tiebreak t : tiebreaks) {
                if (t.round() != round || !ids.contains(t.a()) || !ids.contains(t.b())) {
                    continue;
                }
                int[] a = sums.get(t.a());
                int[] b = sums.get(t.b());
                (t.pointsA() > t.pointsB() ? a : b)[0]++;
                a[1] += t.pointsA();
                a[2] += t.pointsB();
                b[1] += t.pointsB();
                b[2] += t.pointsA();
            }
            Map<Long, double[]> values = new HashMap<>();
            sums.forEach((id, v) -> values.put(id, new double[]{v[0], ratio(v[1], v[2])}));
            return values;
        };
    }

    /**
     * Egalitate nedepartajată prin meciurile directe: fiecare pereche de jucători joacă un set în runda dată.
     * Până se joacă toate, egalitatea rămâne (iar perechile lipsă se adaugă în {@code pending}).
     */
    private static List<List<Stats>> resolveByTiebreaks(List<Stats> tier, int round, List<Tiebreak> tiebreaks,
                                                        List<Pairing> pending) {
        List<Stats> ordered = new ArrayList<>(tier);
        ordered.sort(Comparator.comparingInt(s -> s.competitor.seed()));
        List<Pairing> missing = new ArrayList<>();
        for (int i = 0; i < ordered.size(); i++) {
            for (int j = i + 1; j < ordered.size(); j++) {
                long a = ordered.get(i).competitor.id();
                long b = ordered.get(j).competitor.id();
                boolean played = tiebreaks.stream().anyMatch(t -> t.round() == round
                        && ((t.a() == a && t.b() == b) || (t.a() == b && t.b() == a)));
                if (!played) {
                    missing.add(new Pairing(round, a, b));
                }
            }
        }
        if (!missing.isEmpty()) {
            pending.addAll(missing);
            return List.of(ordered);
        }
        List<List<Stats>> result = new ArrayList<>();
        for (List<Stats> part : resolve(ordered, tiebreakRound(tiebreaks, round))) {
            result.addAll(part.size() > 1 ? resolveByTiebreaks(part, round + 1, tiebreaks, pending) : List.of(part));
        }
        return result;
    }

    /** Împarte jucătorii după o valoare, de la cea mai mare; la valori egale păstrează ordinea din grupă. */
    private static List<List<Stats>> partition(List<Stats> group, Function<Stats, Double> value) {
        Map<Double, List<Stats>> byValue = new TreeMap<>(Comparator.reverseOrder());
        group.stream()
                .sorted(Comparator.comparingInt(s -> s.competitor.seed()))
                .forEach(s -> byValue.computeIfAbsent(value.apply(s), k -> new ArrayList<>()).add(s));
        return new ArrayList<>(byValue.values());
    }

    private static Set<Long> ids(List<Stats> group) {
        Set<Long> ids = new HashSet<>();
        group.forEach(s -> ids.add(s.competitor.id()));
        return ids;
    }

    private static int setsFor(Result r, boolean forA, int setsToWin) {
        if (!r.walkover()) {
            return forA ? r.setsA() : r.setsB();
        }
        boolean aWon = r.winner() == r.a();
        return (forA == aWon) ? setsToWin : 0;
    }

    private static int points(boolean won, boolean walkover) {
        if (won) {
            return POINTS_WIN;
        }
        return walkover ? POINTS_WALKOVER_LOSS : POINTS_LOSS;
    }

    private static double ratio(int won, int lost) {
        if (lost == 0) {
            return won == 0 ? 0 : Double.POSITIVE_INFINITY;
        }
        return (double) won / lost;
    }

    private static final class Stats {
        private final Competitor competitor;
        private final Map<Long, Cell> cells = new HashMap<>();
        private int played;
        private int setsWon;
        private int setsLost;
        private int points;

        private Stats(Competitor competitor) {
            this.competitor = competitor;
        }

        private void add(long opponent, boolean won, boolean walkover, int setsFor, int setsAgainst) {
            int matchPoints = points(won, walkover);
            String score = walkover ? (won ? "W" : "L") : setsFor + ":" + setsAgainst;
            cells.put(opponent, new Cell(matchPoints, score, won, walkover));
            played++;
            setsWon += setsFor;
            setsLost += setsAgainst;
            points += matchPoints;
        }
    }
}
