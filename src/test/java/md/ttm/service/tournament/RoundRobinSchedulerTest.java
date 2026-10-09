package md.ttm.service.tournament;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class RoundRobinSchedulerTest {

    @ParameterizedTest
    @ValueSource(ints = {2, 3, 4, 5, 6, 7, 8, 11, 16})
    void fiecarePerecheJoacaExactOData(int players) {
        List<RoundRobinScheduler.Pairing> pairings = RoundRobinScheduler.schedule(players);

        assertThat(pairings).hasSize(players * (players - 1) / 2);
        Set<String> pairs = new HashSet<>();
        for (RoundRobinScheduler.Pairing p : pairings) {
            assertThat(p.first()).isLessThan(p.second());
            assertThat(p.second()).isLessThan(players);
            assertThat(pairs.add(p.first() + "-" + p.second())).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 5, 8, 9})
    void inFiecareTurUnJucatorAreCelMultUnMeci(int players) {
        Map<Integer, List<RoundRobinScheduler.Pairing>> byRound = RoundRobinScheduler.schedule(players).stream()
                .collect(Collectors.groupingBy(RoundRobinScheduler.Pairing::round));

        int expectedRounds = players % 2 == 0 ? players - 1 : players;
        assertThat(byRound).hasSize(expectedRounds);
        byRound.values().forEach(round -> {
            Set<Integer> seen = new HashSet<>();
            round.forEach(p -> {
                assertThat(seen.add(p.first())).isTrue();
                assertThat(seen.add(p.second())).isTrue();
            });
        });
    }

    @Test
    void sublaDoiJucatoriNuExistaMeciuri() {
        assertThat(RoundRobinScheduler.schedule(0)).isEmpty();
        assertThat(RoundRobinScheduler.schedule(1)).isEmpty();
    }
}
