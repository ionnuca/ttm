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
    void olimpic_cinciJucatori_ceiMaiBuniTrecDirect() {
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
        // locurile celor eliminați se știu deja: 4 și 3 pe locul 3, 5 pe locul 5
        assertThat(semis.placings()).extracting(Bracket.Placing::place).containsExactly(3, 3, 5);

        winners.put("W3-1", 2L);
        Bracket.State done = Bracket.compute(KnockoutType.SINGLE, seeds, winners);
        assertThat(done.complete()).isTrue();
        assertThat(done.placings()).containsExactly(
                new Bracket.Placing(2, 1), new Bracket.Placing(1, 2),
                new Bracket.Placing(4, 3), new Bracket.Placing(3, 3), new Bracket.Placing(5, 5));
    }

    @Test
    void ramuraDeJos_patruJucatori_cuMeciDecisiv() {
        List<Long> seeds = List.of(1L, 2L, 3L, 4L);
        Map<String, Long> winners = new HashMap<>();
        winners.put("W1-1", 1L); // 1–4
        winners.put("W1-2", 2L); // 2–3
        Bracket.State afterRound1 = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(afterRound1.match("L1-1").a()).isEqualTo(4L);
        assertThat(afterRound1.match("L1-1").b()).isEqualTo(3L);
        assertThat(afterRound1.match("W2-1").playable()).isTrue();

        winners.put("L1-1", 3L);
        winners.put("W2-1", 1L);
        winners.put("L2-1", 2L); // 2 (învins în finala tabloului principal) îl bate pe 3
        Bracket.State grandFinal = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(grandFinal.match("GF").a()).isEqualTo(1L);
        assertThat(grandFinal.match("GF").b()).isEqualTo(2L);
        assertThat(grandFinal.placings()).containsExactly(new Bracket.Placing(3, 3), new Bracket.Placing(4, 4));

        // câștigă cel din ramura de jos: meci decisiv
        winners.put("GF", 2L);
        Bracket.State decider = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(decider.complete()).isFalse();
        assertThat(decider.match("GF2").playable()).isTrue();

        winners.put("GF2", 1L);
        Bracket.State done = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(done.complete()).isTrue();
        assertThat(done.placings()).containsExactly(new Bracket.Placing(1, 1), new Bracket.Placing(2, 2),
                new Bracket.Placing(3, 3), new Bracket.Placing(4, 4));

        // dacă marea finală o câștigă cel din tabloul principal, nu se mai joacă meciul decisiv
        winners.put("GF", 1L);
        winners.remove("GF2");
        Bracket.State noDecider = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(noDecider.complete()).isTrue();
        assertThat(noDecider.match("GF2")).isNull();
    }

    @Test
    void ramuraDeJos_treiJucatori_loculLiberTreceDirect() {
        List<Long> seeds = List.of(1L, 2L, 3L);
        Map<String, Long> winners = new HashMap<>();
        Bracket.State start = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        assertThat(Bracket.playable(start)).containsOnlyKeys("W1-2");

        winners.put("W1-2", 3L);
        Bracket.State next = Bracket.compute(KnockoutType.DOUBLE, seeds, winners);
        // 2 a pierdut și trece direct în turul 2 al ramurii de jos (în turul 1 n-are adversar)
        assertThat(next.match("L1-1").winner()).isEqualTo(2L);
        assertThat(Bracket.playable(next)).containsOnlyKeys("W1-2", "W2-1");
    }

    @Test
    void doiJucatori() {
        Map<String, Long> winners = new HashMap<>(Map.of("W1-1", 2L));
        Bracket.State single = Bracket.compute(KnockoutType.SINGLE, List.of(1L, 2L), winners);
        assertThat(single.complete()).isTrue();
        assertThat(single.placings()).containsExactly(new Bracket.Placing(2, 1), new Bracket.Placing(1, 2));

        Bracket.State doubleElim = Bracket.compute(KnockoutType.DOUBLE, List.of(1L, 2L), winners);
        assertThat(doubleElim.match("GF").a()).isEqualTo(2L);
        assertThat(doubleElim.match("GF").b()).isEqualTo(1L);
        assertThat(doubleElim.complete()).isFalse();
    }
}
