package md.ttm.service.tournament;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class GroupStagePlannerTest {

    @Test
    void fiecareGrupaPrimesteCateUnJucatorDinFiecareUrna() {
        for (long seed = 0; seed < 50; seed++) {
            List<List<Integer>> groups = GroupStagePlanner.draw(12, 3, new Random(seed));
            assertThat(groups).allSatisfy(g -> {
                assertThat(g).hasSize(4).isSorted();
                // urnele: 0-2, 3-5, 6-8, 9-11 — câte un jucător din fiecare
                assertThat(g.stream().map(i -> i / 3).toList()).containsExactly(0, 1, 2, 3);
            });
            assertThat(groups.stream().flatMap(List::stream).sorted().toList())
                    .isEqualTo(IntStream.range(0, 12).boxed().toList());
        }
    }

    @Test
    void ultimaUrnaIncompletaAjungeInGrupeDiferite() {
        for (long seed = 0; seed < 50; seed++) {
            List<List<Integer>> groups = GroupStagePlanner.draw(11, 3, new Random(seed));
            assertThat(groups.stream().map(List::size).sorted().toList()).containsExactly(3, 4, 4);
            assertThat(groups).allSatisfy(g -> assertThat(g.stream().map(i -> i / 3).distinct().count()).isEqualTo(g.size()));
        }
        assertThat(GroupStagePlanner.groupSizes(11, 3)).containsExactly(4, 4, 3);
    }

    @Test
    void tragereaLaSortiDiferaDeLaOTragereLaAlta() {
        Set<List<List<Integer>>> draws = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            draws.add(GroupStagePlanner.draw(8, 2, new Random(seed)));
        }
        assertThat(draws).hasSizeGreaterThan(1);
    }

    @Test
    void programulFinaleiSarePestePerechileCareAuJucatDeja() {
        // 4 finaliști: 0 și 1 din grupa A, 2 și 3 din grupa B
        int[] group = {0, 0, 1, 1};
        List<RoundRobinScheduler.Pairing> pairings = GroupStagePlanner.scheduleWithout(4,
                (a, b) -> group[a] == group[b]);

        assertThat(pairings).hasSize(4);
        assertThat(pairings).noneMatch(p -> group[p.first()] == group[p.second()]);
        assertThat(pairings).extracting(RoundRobinScheduler.Pairing::round).containsOnly(1, 2);
    }
}
