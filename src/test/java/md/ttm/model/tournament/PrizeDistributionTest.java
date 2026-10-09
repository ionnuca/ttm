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
        assertThat(PrizeDistribution.THREE_WINNERS.split(new BigDecimal("800")))
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("240.00"), new BigDecimal("160.00"));
        assertThat(PrizeDistribution.TWO_WINNERS.split(new BigDecimal("1000")))
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("400.00"));
    }

    @Test
    void totalulPremiilorEsteExactSumaAcumulata() {
        BigDecimal pool = new BigDecimal("100.01");
        BigDecimal total = PrizeDistribution.THREE_WINNERS.split(pool).stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo(pool);
    }
}
