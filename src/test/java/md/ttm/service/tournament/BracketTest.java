package md.ttm.service.tournament;

import md.ttm.model.tournament.KnockoutType;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BracketTest {

    @Test
    void asezareaStandardACapilorDeSerie() {
        assertThat(Bracket.positions(8)).containsExactly(1, 8, 4, 5, 2, 7, 3, 6);
        assertThat(Bracket.positions(4)).containsExactly(1, 4, 2, 3);
    }

    @Test
    void olimpic_cinciJucatori_ceiMaiBuniTrecDirect_meciPentruLocul3() {
        List<Long> seeds = List.of(1L, 2L, 3L, 4L, 5L);
        Map<String, Long> winners = new HashMap<>();

        Bracket.State start = Bracket.compute(KnockoutType.SINGLE, seeds, winners);
        assertThat(start.size()).isEqualTo(8);
        assertThat(Bracket.playable(start)).containsOnlyKeys("W1-2", "W2-2"); // 4–5 și 2–3
        assertThat(start.complete()).isFalse();

        winners.put("W1-2", 4L);
        winners.put("W2-1", 1L);
        winners.put("W2-2", 2L);
        Bracket.State semis = Bracket.compute(KnockoutType.SINGLE, seeds, winners);
        assertThat(semis.match("W3-1").a()).isEqualTo(1L);
        assertThat(semis.match("W3-1").b()).isEqualTo(2L);
        // învinșii din semifinale joacă pentru locul 3
        assertThat(semis.match(Bracket.THIRD_PLACE).a()).isEqualTo(4L);
        assertThat(semis.match(Bracket.THIRD_PLACE).b()).isEqualTo(3L);
        assertThat(semis.placings()).containsExactly(new Bracket.Placing(5, 5));

        winners.put("W3-1", 2L);
        assertThat(Bracket.compute(KnockoutType.SINGLE, seeds, winners).complete()).isFalse();
        winners.put(Bracket.THIRD_PLACE, 3L);
        Bracket.State done = Bracket.compute(KnockoutType.SINGLE, seeds, winners);
        assertThat(done.complete()).isTrue();
        assertThat(done.placings()).containsExactly(
                new Bracket.Placing(2, 1), new Bracket.Placing(1, 2),
                new Bracket.Placing(3, 3), new Bracket.Placing(4, 4), new Bracket.Placing(5, 5));
    }

    @Test
    void ramuraDeJos_patruJucatori_finalaRamuriiDeJosEsteLocul3() {
        List<Long> seeds = List.of(1L, 2L, 3L, 4L);
        Map<String, Long> winners = new HashMap<>();
        winners.put("W1-1", 1L); // 1–4
        winners.put("W1-2", 2L); // 2–3
        Bracket.State afterRound1 = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(afterRound1.match("L1-1").a()).isEqualTo(4L);
        assertThat(afterRound1.match("L1-1").b()).isEqualTo(3L);
        assertThat(afterRound1.match("W2-1").playable()).isTrue();

        winners.put("L1-1", 3L);
        Bracket.State lowerDone = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(lowerDone.complete()).isFalse();
        assertThat(lowerDone.placings()).containsExactly(new Bracket.Placing(3, 3), new Bracket.Placing(4, 4));

        winners.put("W2-1", 2L);
        Bracket.State done = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(done.complete()).isTrue();
        assertThat(done.placings()).containsExactly(new Bracket.Placing(2, 1), new Bracket.Placing(1, 2),
                new Bracket.Placing(3, 3), new Bracket.Placing(4, 4));
        assertThat(done.matches()).noneMatch(m -> m.section() == Bracket.Section.FINAL);
    }

    @Test
    void ramuraDeJos_optJucatori() {
        List<Long> seeds = List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L);
        Map<String, Long> winners = new HashMap<>();
        winners.put("W1-1", 1L); // 1–8
        winners.put("W1-2", 4L); // 4–5
        winners.put("W1-3", 2L); // 2–7
        winners.put("W1-4", 3L); // 3–6
        winners.put("L1-1", 5L); // 8–5
        winners.put("L1-2", 6L); // 7–6
        winners.put("W2-1", 1L); // 1–4
        winners.put("W2-2", 2L); // 2–3
        Bracket.State state = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        // învinșii din semifinale intră în ramura de jos în ordine inversă
        assertThat(state.match("L2-1").a()).isEqualTo(5L);
        assertThat(state.match("L2-1").b()).isEqualTo(3L);
        assertThat(state.match("L2-2").a()).isEqualTo(6L);
        assertThat(state.match("L2-2").b()).isEqualTo(4L);

        winners.put("L2-1", 3L);
        winners.put("L2-2", 4L);
        winners.put("L3-1", 3L); // finala ramurii de jos: locul 3
        winners.put("W3-1", 1L); // finala: locul 1
        Bracket.State done = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(done.complete()).isTrue();
        assertThat(done.match("L3-1").rounds()).isEqualTo(3);
        assertThat(done.placings()).containsExactly(
                new Bracket.Placing(1, 1), new Bracket.Placing(2, 2),
                new Bracket.Placing(3, 3), new Bracket.Placing(4, 4),
                new Bracket.Placing(5, 5), new Bracket.Placing(6, 5),
                new Bracket.Placing(8, 7), new Bracket.Placing(7, 7));
    }

    @Test
    void ramuraDeJos_treiJucatori_loculLiberTreceDirect() {
        List<Long> seeds = List.of(1L, 2L, 3L);
        Map<String, Long> winners = new HashMap<>();
        Bracket.State start = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(Bracket.playable(start)).containsOnlyKeys("W1-2");

        winners.put("W1-2", 3L);
        Bracket.State next = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        // 2 a pierdut și n-are adversar în ramura de jos: e pe locul 3
        assertThat(next.match("L1-1").winner()).isEqualTo(2L);
        assertThat(Bracket.playable(next)).containsOnlyKeys("W1-2", "W2-1");
        assertThat(next.placings()).containsExactly(new Bracket.Placing(2, 3));
    }

    @Test
    void doiJucatori() {
        Map<String, Long> winners = new HashMap<>(Map.of("W1-1", 2L));
        for (KnockoutType type : KnockoutType.values()) {
            Bracket.State state = Bracket.compute(type, List.of(1L, 2L), winners);
            assertThat(state.complete()).isTrue();
            assertThat(state.matches()).hasSize(1);
            assertThat(state.placings()).containsExactly(new Bracket.Placing(2, 1), new Bracket.Placing(1, 2));
        }
    }
}
