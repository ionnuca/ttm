package md.ttm.service.tournament;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.random.RandomGenerator;

/**
 * Calculele pentru turneul „Grupe + finale”, fără dependențe de baza de date.
 */
public final class GroupStagePlanner {

    private GroupStagePlanner() {
    }

    /**
     * Repartizarea pe urne cu tragere la sorți. Jucătorii sunt ordonați după rating (index 0 = cel mai bun);
     * primii {@code groups} formează prima urnă și se împart aleatoriu câte unul în fiecare grupă,
     * următorii {@code groups} la fel, și tot așa. Din ultima urnă, incompletă, jucătorii ajung în grupe
     * alese tot aleatoriu (câte unul în grupă).
     * <p>
     * Astfel fiecare grupă are câte un jucător din fiecare nivel de rating, iar cei mai buni
     * ({@code groups} la număr) sunt în grupe diferite.
     *
     * @return pentru fiecare grupă, indicii jucătorilor, în ordinea ratingului
     */
    public static List<List<Integer>> draw(int players, int groups, RandomGenerator random) {
        List<List<Integer>> result = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            result.add(new ArrayList<>());
        }
        List<Integer> order = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            order.add(g);
        }
        for (int potStart = 0; potStart < players; potStart += groups) {
            shuffle(order, random);
            int potSize = Math.min(groups, players - potStart);
            for (int k = 0; k < potSize; k++) {
                result.get(order.get(k)).add(potStart + k);
            }
        }
        return result;
    }

    /** Mărimile grupelor, de la cea mai mare, de exemplu [4, 4, 3] pentru 11 jucători în 3 grupe. */
    public static List<Integer> groupSizes(int players, int groups) {
        List<Integer> sizes = new ArrayList<>();
        for (int g = 0; g < groups; g++) {
            sizes.add(players / groups + (g < players % groups ? 1 : 0));
        }
        return sizes;
    }

    private static void shuffle(List<Integer> list, RandomGenerator random) {
        for (int i = list.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Integer tmp = list.get(i);
            list.set(i, list.get(j));
            list.set(j, tmp);
        }
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
