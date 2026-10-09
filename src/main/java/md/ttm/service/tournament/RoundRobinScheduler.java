package md.ttm.service.tournament;

import java.util.ArrayList;
import java.util.List;

/**
 * Generează meciurile unui turneu Round Robin (fiecare cu fiecare) prin metoda cercului
 * (tabelele Berger): fiecare pereche joacă o singură dată, iar într-un tur un jucător
 * are cel mult un meci. La număr impar de jucători, în fiecare tur unul stă (bye).
 */
public final class RoundRobinScheduler {

    /** Un meci programat: turul și pozițiile (0 = primul cap de serie) celor doi jucători. */
    public record Pairing(int round, int first, int second) {
    }

    private RoundRobinScheduler() {
    }

    public static List<Pairing> schedule(int players) {
        List<Pairing> pairings = new ArrayList<>();
        if (players < 2) {
            return pairings;
        }
        List<Integer> circle = new ArrayList<>();
        for (int i = 0; i < players; i++) {
            circle.add(i);
        }
        if (players % 2 == 1) {
            circle.add(-1); // bye
        }
        int size = circle.size();
        for (int round = 1; round < size; round++) {
            for (int i = 0; i < size / 2; i++) {
                int a = circle.get(i);
                int b = circle.get(size - 1 - i);
                if (a >= 0 && b >= 0) {
                    pairings.add(new Pairing(round, Math.min(a, b), Math.max(a, b)));
                }
            }
            // primul rămâne pe loc, ceilalți se rotesc cu o poziție
            circle.add(1, circle.remove(size - 1));
        }
        return pairings;
    }
}
