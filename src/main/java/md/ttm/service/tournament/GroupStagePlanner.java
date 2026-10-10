package md.ttm.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;

/**
 * Calculele pentru turneul „Grupe + finale”, fără dependențe de baza de date.
 */
public final class GroupStagePlanner {

    private GroupStagePlanner() {
    }

    /**
     * Repartizarea în șerpuială: jucătorii ordonați după rating (index 0 = cel mai bun)
     * se împart pe rânduri: 1→A, 2→B, 3→C, apoi 4→C, 5→B, 6→A, 7→A …
     *
     * @return pentru fiecare grupă, indicii jucătorilor, în ordinea din grupă
     */
    public static List<List<Integer>> snake(int players, int groups) {
        List<List<Integer>> result = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            result.add(new ArrayList<>());
        }
        for (int i = 0; i < players; i++) {
            int row = i / groups;
            int column = i % groups;
            int group = row % 2 == 0 ? column : groups - 1 - column;
            result.get(group).add(i);
        }
        return result;
    }

    /** Mărimile grupelor, de exemplu [4, 4, 3] pentru 11 jucători în 3 grupe. */
    public static List<Integer> groupSizes(int players, int groups) {
        return snake(players, groups).stream().map(List::size).toList();
    }

    /**
     * Programul Round Robin al unei grupe, fără perechile care s-au întâlnit deja
     * (rezultatul lor se preia). Tururile rămase goale se elimină, iar cele rămase se renumerotează.
     *
     * @param alreadyPlayed primește doi indici din grupă și spune dacă jucătorii s-au întâlnit deja
     */
    public static List<RoundRobinScheduler.Pairing> scheduleWithout(int players,
                                                                   BiPredicate<Integer, Integer> alreadyPlayed) {
        List<RoundRobinScheduler.Pairing> kept = RoundRobinScheduler.schedule(players).stream()
                .filter(p -> !alreadyPlayed.test(p.first(), p.second()))
                .toList();
        List<RoundRobinScheduler.Pairing> renumbered = new ArrayList<>();
        int lastOriginal = -1;
        int round = 0;
        for (RoundRobinScheduler.Pairing p : kept) {
            if (p.round() != lastOriginal) {
                round++;
                lastOriginal = p.round();
            }
            renumbered.add(new RoundRobinScheduler.Pairing(round, p.first(), p.second()));
        }
        return renumbered;
    }
}
