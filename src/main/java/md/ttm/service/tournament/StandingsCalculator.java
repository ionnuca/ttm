package md.ttm.service.tournament;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Calculează tabelul unui turneu Round Robin: rezultatul fiecărei întâlniri, seturile,
 * punctele și locul fiecărui jucător.
 * <p>
 * Puncte: 2 pentru victorie, 1 pentru înfrângere, 0 pentru înfrângere tehnică (W).
 * O victorie tehnică se socotește la seturi ca victorie la scor alb (ex. 3:0 la „best of 5”).
 * <p>
 * Departajarea la egalitate de puncte (după regulile ITTF, simplificat):
 * <ol>
 *     <li>punctele din meciurile directe dintre jucătorii aflați la egalitate;</li>
 *     <li>raportul seturilor câștigate/pierdute din aceste meciuri;</li>
 *     <li>raportul seturilor din toate meciurile;</li>
 *     <li>poziția în grupă (ratingul de la începutul turneului).</li>
 * </ol>
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
     * @param place locul în clasament; 0 dacă încă nu s-a jucat niciun meci în turneu
     */
    public record Row(long id, int seed, Map<Long, Cell> cells, int played, int setsWon, int setsLost,
                      int points, int place) {
    }

    private StandingsCalculator() {
    }

    /**
     * @param competitors jucătorii, în ordinea din grupă
     * @param results     meciurile jucate
     * @param setsToWin   seturile necesare pentru a câștiga un meci
     * @return rândurile tabelului, în ordinea din grupă (locul e în {@link Row#place()})
     */
    public static List<Row> compute(List<Competitor> competitors, List<Result> results, int setsToWin) {
        Map<Long, Stats> stats = new LinkedHashMap<>();
        for (Competitor c : competitors) {
            stats.put(c.id(), new Stats(c));
        }
        for (Result r : results) {
            Stats a = stats.get(r.a());
            Stats b = stats.get(r.b());
            if (a == null || b == null) {
                continue;
            }
            boolean aWon = r.winner() == r.a();
            int setsA = r.walkover() ? (aWon ? setsToWin : 0) : r.setsA();
            int setsB = r.walkover() ? (aWon ? 0 : setsToWin) : r.setsB();
            a.add(r.b(), aWon, r.walkover(), setsA, setsB);
            b.add(r.a(), !aWon, r.walkover(), setsB, setsA);
        }

        Map<Long, Integer> places = new HashMap<>();
        if (!results.isEmpty()) {
            List<Stats> ordered = rank(new ArrayList<>(stats.values()), results, setsToWin);
            for (int i = 0; i < ordered.size(); i++) {
                places.put(ordered.get(i).competitor.id(), i + 1);
            }
        }

        List<Row> rows = new ArrayList<>();
        for (Stats s : stats.values()) {
            rows.add(new Row(s.competitor.id(), s.competitor.seed(), Map.copyOf(s.cells), s.played,
                    s.setsWon, s.setsLost, s.points, places.getOrDefault(s.competitor.id(), 0)));
        }
        return rows;
    }

    private static List<Stats> rank(List<Stats> all, List<Result> results, int setsToWin) {
        Map<Integer, List<Stats>> byPoints = all.stream()
                .collect(Collectors.groupingBy(s -> s.points, LinkedHashMap::new, Collectors.toList()));
        List<Integer> pointLevels = new ArrayList<>(byPoints.keySet());
        pointLevels.sort(Comparator.reverseOrder());

        List<Stats> ordered = new ArrayList<>();
        for (int level : pointLevels) {
            List<Stats> group = byPoints.get(level);
            if (group.size() > 1) {
                breakTie(group, results, setsToWin);
            }
            ordered.addAll(group);
        }
        return ordered;
    }

    /** Ordonează un grup de jucători cu aceleași puncte. */
    private static void breakTie(List<Stats> group, List<Result> results, int setsToWin) {
        Set<Long> ids = new HashSet<>();
        group.forEach(s -> ids.add(s.competitor.id()));

        Map<Long, int[]> mini = new HashMap<>(); // [puncte, seturi câștigate, seturi pierdute]
        ids.forEach(id -> mini.put(id, new int[3]));
        for (Result r : results) {
            if (!ids.contains(r.a()) || !ids.contains(r.b())) {
                continue;
            }
            boolean aWon = r.winner() == r.a();
            int setsA = r.walkover() ? (aWon ? setsToWin : 0) : r.setsA();
            int setsB = r.walkover() ? (aWon ? 0 : setsToWin) : r.setsB();
            int[] a = mini.get(r.a());
            int[] b = mini.get(r.b());
            a[0] += points(aWon, r.walkover());
            b[0] += points(!aWon, r.walkover());
            a[1] += setsA;
            a[2] += setsB;
            b[1] += setsB;
            b[2] += setsA;
        }

        Comparator<Stats> comparator = Comparator
                .comparingInt((Stats s) -> -mini.get(s.competitor.id())[0])
                .thenComparing((Stats s) -> -ratio(mini.get(s.competitor.id())[1], mini.get(s.competitor.id())[2]))
                .thenComparing((Stats s) -> -ratio(s.setsWon, s.setsLost))
                .thenComparingInt(s -> s.competitor.seed());
        group.sort(comparator);
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
