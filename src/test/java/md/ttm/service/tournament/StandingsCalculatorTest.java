package md.ttm.service.tournament;

import md.ttm.service.tournament.StandingsCalculator.Competitor;
import md.ttm.service.tournament.StandingsCalculator.Result;
import md.ttm.service.tournament.StandingsCalculator.Row;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class StandingsCalculatorTest {

    private static final List<Competitor> FOUR = List.of(
            new Competitor(1, 1), new Competitor(2, 2), new Competitor(3, 3), new Competitor(4, 4));

    @Test
    void punctePentruVictorieInfrangereSiInfrangereTehnica() {
        List<Row> rows = StandingsCalculator.compute(FOUR, List.of(
                win(1, 2, 3, 1),          // 1 bate 2 cu 3:1
                walkover(3, 4, 3)          // 3 câștigă tehnic cu 4
        ), 3);
        Map<Long, Row> byId = byId(rows);

        assertThat(byId.get(1L).points()).isEqualTo(2);
        assertThat(byId.get(2L).points()).isEqualTo(1);
        assertThat(byId.get(3L).points()).isEqualTo(2);
        assertThat(byId.get(4L).points()).isZero();

        StandingsCalculator.Cell cell = byId.get(1L).cells().get(2L);
        assertThat(cell.points()).isEqualTo(2);
        assertThat(cell.score()).isEqualTo("3:1");
        assertThat(byId.get(2L).cells().get(1L).score()).isEqualTo("1:3");
        assertThat(byId.get(3L).cells().get(4L).score()).isEqualTo("W");
        assertThat(byId.get(4L).cells().get(3L).score()).isEqualTo("L");
        assertThat(byId.get(4L).cells().get(3L).points()).isZero();
    }

    @Test
    void victoriaTehnicaSeSocotesteLaSeturiCaScorAlb() {
        Map<Long, Row> byId = byId(StandingsCalculator.compute(FOUR, List.of(walkover(3, 4, 3)), 3));
        assertThat(byId.get(3L).setsWon()).isEqualTo(3);
        assertThat(byId.get(3L).setsLost()).isZero();
        assertThat(byId.get(4L).setsWon()).isZero();
        assertThat(byId.get(4L).setsLost()).isEqualTo(3);
    }

    @Test
    void clasamentulDupaPuncteSiMeciDirectLaEgalitate() {
        // 1: învinge 3, 4, pierde cu 2  -> 2+2+1 = 5
        // 2: învinge 1, 4, pierde cu 3  -> 5
        // 3: învinge 2, pierde cu 1, 4  -> 2+1+1 = 4
        // 4: învinge 3, pierde cu 1, 2  -> 4
        List<Result> results = List.of(
                win(2, 1, 3, 2), win(1, 3, 3, 0), win(1, 4, 3, 1),
                win(3, 2, 3, 2), win(2, 4, 3, 0), win(4, 3, 3, 1));
        Map<Long, Row> byId = byId(StandingsCalculator.compute(FOUR, results, 3));

        assertThat(byId.get(2L).place()).isEqualTo(1); // l-a învins direct pe 1
        assertThat(byId.get(1L).place()).isEqualTo(2);
        assertThat(byId.get(4L).place()).isEqualTo(3); // l-a învins direct pe 3
        assertThat(byId.get(3L).place()).isEqualTo(4);
    }

    @Test
    void egalitateIntreTreiSeDecideDupaRaportulSeturilorDinMeciurileDirecte() {
        List<Competitor> three = List.of(new Competitor(1, 1), new Competitor(2, 2), new Competitor(3, 3));
        // Fiecare câștigă câte un meci: toți au 3 puncte
        List<Result> results = List.of(
                win(1, 2, 3, 0),   // 1: 3:0
                win(2, 3, 3, 2),   // 2: 3:2
                win(3, 1, 3, 1));  // 3: 3:1
        Map<Long, Row> byId = byId(StandingsCalculator.compute(three, results, 3));
        // seturi: 1 -> 3+1 = 4 câștigate / 0+3 = 3 pierdute (1.33)
        //         2 -> 0+3 = 3 / 3+2 = 5 (0.6)
        //         3 -> 2+3 = 5 / 3+1 = 4 (1.25)
        assertThat(byId.get(1L).place()).isEqualTo(1);
        assertThat(byId.get(3L).place()).isEqualTo(2);
        assertThat(byId.get(2L).place()).isEqualTo(3);
    }

    @Test
    void faraMeciuriJucateLocurileSuntNecompletate() {
        List<Row> rows = StandingsCalculator.compute(FOUR, List.of(), 3);
        assertThat(rows).extracting(Row::place).containsOnly(0);
        assertThat(rows).extracting(Row::id).containsExactly(1L, 2L, 3L, 4L);
    }

    private static Result win(long winner, long loser, int winnerSets, int loserSets) {
        return new Result(winner, loser, winnerSets, loserSets, winner, false);
    }

    private static Result walkover(long winner, long loser, int setsToWin) {
        return new Result(winner, loser, 0, 0, winner, true);
    }

    private static Map<Long, Row> byId(List<Row> rows) {
        return rows.stream().collect(Collectors.toMap(Row::id, Function.identity()));
    }
}
