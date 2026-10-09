package md.ttm.service.rating;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EloTest {

    @Test
    void probabilitateaAsteptata() {
        assertThat(Elo.expectedScore(1000, 1000)).isEqualTo(0.5);
        assertThat(Elo.expectedScore(1420, 1350)).isCloseTo(0.599, within(0.001));
        assertThat(Elo.expectedScore(1350, 1420)).isCloseTo(0.401, within(0.001));
        assertThat(Elo.expectedScore(1400, 1000)).isCloseTo(0.909, within(0.001));
    }

    @Test
    void diferentaPeste400SeSocotesteCa400() {
        assertThat(Elo.expectedScore(2000, 1000)).isEqualTo(Elo.expectedScore(1400, 1000));
        assertThat(Elo.expectedScore(1000, 2000)).isEqualTo(Elo.expectedScore(1000, 1400));
    }

    @Test
    void factorulK() {
        assertThat(Elo.kFactor(0)).isEqualTo(40);
        assertThat(Elo.kFactor(29)).isEqualTo(40);
        assertThat(Elo.kFactor(30)).isEqualTo(20);
    }

    @Test
    void rotunjireaDepartantDeZero() {
        assertThat(Elo.round(2.5)).isEqualTo(3);
        assertThat(Elo.round(-2.5)).isEqualTo(-3);
        assertThat(Elo.round(2.4)).isEqualTo(2);
        assertThat(Elo.round(-2.4)).isEqualTo(-2);
        assertThat(Elo.round(0.0)).isZero();
    }

    @Test
    void exempluCuK20() {
        // 1420 vs 1350: favoritul câștigă +8, outsiderul câștigă +12
        assertThat(Elo.round(Elo.change(1420, 1350, true, 20))).isEqualTo(8);
        assertThat(Elo.round(Elo.change(1350, 1420, true, 20))).isEqualTo(12);
        assertThat(Elo.round(Elo.change(1420, 1350, false, 20))).isEqualTo(-12);
    }

    @Test
    void turneulSeCalculeazaDinRatingurileDeDinainteSiSeRotunjesteOSinguraData() {
        var result = TournamentRatingCalculator.calculate(
                List.of(new TournamentRatingCalculator.Entrant(1, 1000, 0),
                        new TournamentRatingCalculator.Entrant(2, 1000, 0),
                        new TournamentRatingCalculator.Entrant(3, 1000, 50)),
                List.of(new TournamentRatingCalculator.Game(10, 1, 2, true),
                        new TournamentRatingCalculator.Game(11, 1, 3, true),
                        new TournamentRatingCalculator.Game(12, 2, 3, false)));

        // 1: două victorii la egalitate de rating, K = 40 -> +20 +20
        assertThat(result.ratingAfter().get(1L)).isEqualTo(1040);
        // 2: o înfrângere (-20) și o înfrângere (-20)
        assertThat(result.ratingAfter().get(2L)).isEqualTo(960);
        // 3: K = 20 (are deja 50 de meciuri): -10 +10
        assertThat(result.ratingAfter().get(3L)).isEqualTo(1000);
        assertThat(result.kFactor()).containsEntry(3L, 20).containsEntry(1L, 40);
        assertThat(result.ratedMatches()).containsEntry(1L, 2);
        assertThat(result.gameDeltas().get(10L)).isEqualTo(new TournamentRatingCalculator.GameDelta(20, -20));
    }
}
