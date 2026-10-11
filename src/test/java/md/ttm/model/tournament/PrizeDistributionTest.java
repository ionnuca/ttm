package md.ttm.model.tournament;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class PrizeDistributionTest {

    @Test
    void procenteleDinSpecificatie() {
        assertThat(PrizeDistribution.forWinners(1).percentages()).containsExactly(100);
        assertThat(PrizeDistribution.forWinners(2).percentages()).containsExactly(60, 40);
        assertThat(PrizeDistribution.forWinners(3).percentages()).containsExactly(50, 30, 20);
    }

    @Test
    void impartireaSumeiAcumulate() {
        // 400 / 240 / 160 -> rotunjite la 50
        assertThat(PrizeDistribution.THREE_WINNERS.split(new BigDecimal("800")))
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("250.00"), new BigDecimal("150.00"));
        assertThat(PrizeDistribution.TWO_WINNERS.split(new BigDecimal("1000")))
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("400.00"));
    }

    @Test
    void premiileSeRotunjescLaCelMaiApropiatMultipluDe50() {
        // 420 / 280
        assertThat(PrizeDistribution.TWO_WINNERS.split(new BigDecimal("700")))
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("300.00"));
        // 105 / 63 / 42
        assertThat(PrizeDistribution.THREE_WINNERS.split(new BigDecimal("210")))
                .containsExactly(new BigDecimal("100.00"), new BigDecimal("50.00"), new BigDecimal("50.00"));
        // 225 -> 250 (la jumătate, în sus); 135 -> 150; 90 -> 100
        assertThat(PrizeDistribution.THREE_WINNERS.split(new BigDecimal("450")))
                .containsExactly(new BigDecimal("250.00"), new BigDecimal("150.00"), new BigDecimal("100.00"));
        assertThat(PrizeDistribution.ONE_WINNER.split(new BigDecimal("380")))
                .containsExactly(new BigDecimal("400.00"));
    }
}
