package md.ttm.service.tournament;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class GroupStagePlannerTest {

    @Test
    void serpuialaEchilibreazaGrupele() {
        // 8 jucători, 2 grupe: A = 1,4,5,8; B = 2,3,6,7 (numerotare de la 1)
        List<List<Integer>> groups = GroupStagePlanner.snake(8, 2);
        assertThat(groups.get(0)).containsExactly(0, 3, 4, 7);
        assertThat(groups.get(1)).containsExactly(1, 2, 5, 6);
    }

    @Test
    void serpuialaCuTreiGrupeSiNumarImpar() {
        List<List<Integer>> groups = GroupStagePlanner.snake(11, 3);
        assertThat(groups.get(0)).containsExactly(0, 5, 6);
        assertThat(groups.get(1)).containsExactly(1, 4, 7, 10);
        assertThat(groups.get(2)).containsExactly(2, 3, 8, 9);
        assertThat(GroupStagePlanner.groupSizes(11, 3)).containsExactly(3, 4, 4);
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
