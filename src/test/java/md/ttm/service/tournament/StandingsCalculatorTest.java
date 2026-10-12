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
    void egalitateaTotalaImparteLocul() {
        List<Competitor> three = List.of(new Competitor(1, 1), new Competitor(2, 2), new Competitor(3, 3));
        // cerc perfect, toate 3:0
        List<Result> results = List.of(win(1, 2, 3, 0), win(2, 3, 3, 0), win(3, 1, 3, 0));
        List<Row> rows = StandingsCalculator.compute(three, results, 3);
        assertThat(rows).extracting(Row::place).containsOnly(1);
        assertThat(rows).allMatch(Row::sharedPlace);
    }

    @Test
    void numaiMeciurileDirecteConteaza_nuSeturileDinToateMeciurile() {
        // 3 și 4 au aceleași puncte, dar nu au jucat încă între ei: rămân pe același loc,
        // deși raportul seturilor din celelalte meciuri diferă (3: 3/3, 4: 3/3 … nu contează)
        List<Result> results = List.of(
                win(1, 2, 3, 2), win(1, 3, 3, 0), win(2, 4, 3, 0),
                win(4, 1, 3, 0), win(3, 2, 3, 0));
        Map<Long, Row> byId = byId(StandingsCalculator.compute(FOUR, results, 3));
        assertThat(byId.get(1L).points()).isEqualTo(5);
        assertThat(byId.get(2L).points()).isEqualTo(4);
        assertThat(byId.get(3L).points()).isEqualTo(3);
        assertThat(byId.get(4L).points()).isEqualTo(3);
        assertThat(byId.get(3L).place()).isEqualTo(3);
        assertThat(byId.get(4L).place()).isEqualTo(3);
        assertThat(byId.get(3L).sharedPlace()).isTrue();
        assertThat(byId.get(1L).sharedPlace()).isFalse();
    }

    @Test
    void cuCeiRamasiLaEgalitateCalcululSeReiaDoarIntreEi() {
        // 1, 2, 3 au câte 2 victorii (5 puncte), 4 nu are nicio victorie
        // între 1, 2, 3: 1 bate 2 3:0, 2 bate 3 3:0, 3 bate 1 3:2
        // raportul seturilor între ei: 1 = 5/3, 2 = 3/3, 3 = 3/5 → 1, 2, 3
        List<Result> results = List.of(
                win(1, 2, 3, 0), win(2, 3, 3, 0), win(3, 1, 3, 2),
                win(1, 4, 3, 0), win(2, 4, 3, 0), win(3, 4, 3, 0));
        Map<Long, Row> byId = byId(StandingsCalculator.compute(FOUR, results, 3));
        assertThat(byId.get(1L).place()).isEqualTo(1);
        assertThat(byId.get(2L).place()).isEqualTo(2);
        assertThat(byId.get(3L).place()).isEqualTo(3);
        assertThat(byId.get(4L).place()).isEqualTo(4);

        List<Result> second = List.of(
                win(1, 2, 3, 0), win(2, 3, 3, 1), win(3, 1, 3, 1),
                win(1, 4, 3, 0), win(2, 4, 3, 0), win(3, 4, 3, 0));
        // seturi între ei: 1 = 4/3, 2 = 3/4, 3 = 4/4
        Map<Long, Row> other = byId(StandingsCalculator.compute(FOUR, second, 3));
        assertThat(other.get(1L).place()).isEqualTo(1);
        assertThat(other.get(3L).place()).isEqualTo(2);
        assertThat(other.get(2L).place()).isEqualTo(3);
    }

    @Test
    void turneulComercialCereSeturiDeDepartajare() {
        List<Competitor> three = List.of(new Competitor(1, 1), new Competitor(2, 2), new Competitor(3, 3));
        List<Result> results = List.of(win(1, 2, 3, 0), win(2, 3, 3, 0), win(3, 1, 3, 0));

        StandingsCalculator.Table table = StandingsCalculator.table(three, results, 3, true, List.of());
        assertThat(table.tiebreaks()).containsExactly(
                new StandingsCalculator.Pairing(1, 1, 2),
                new StandingsCalculator.Pairing(1, 1, 3),
                new StandingsCalculator.Pairing(1, 2, 3));
        assertThat(table.rows()).extracting(Row::place).containsOnly(1);

        // runda 1: fiecare câștigă câte un set; raportul punctelor decide
        // 1: 11:9 cu 2, 5:11 cu 3 → 16/20; 2: 9:11, 11:4 cu 3 → 20/15; 3: 11:5, 4:11 → 15/16
        List<StandingsCalculator.Tiebreak> round1 = List.of(
                new StandingsCalculator.Tiebreak(1, 1, 2, 11, 9),
                new StandingsCalculator.Tiebreak(1, 1, 3, 5, 11),
                new StandingsCalculator.Tiebreak(1, 2, 3, 11, 4));
        table = StandingsCalculator.table(three, results, 3, true, round1);
        assertThat(table.tiebreaks()).isEmpty();
        Map<Long, Row> byId = byId(table.rows());
        assertThat(byId.get(2L).place()).isEqualTo(1);
        assertThat(byId.get(3L).place()).isEqualTo(2);
        assertThat(byId.get(1L).place()).isEqualTo(3);
        assertThat(table.rows()).noneMatch(Row::sharedPlace);
    }

    @Test
    void laEgalitateInSeturileDeDepartajareSeJoacaORundaNoua() {
        List<Competitor> three = List.of(new Competitor(1, 1), new Competitor(2, 2), new Competitor(3, 3));
        List<Result> results = List.of(win(1, 2, 3, 0), win(2, 3, 3, 0), win(3, 1, 3, 0));
        // fiecare câștigă un set cu 11:9 → aceleași puncte (20/20)
        List<StandingsCalculator.Tiebreak> round1 = List.of(
                new StandingsCalculator.Tiebreak(1, 1, 2, 11, 9),
                new StandingsCalculator.Tiebreak(1, 2, 3, 11, 9),
                new StandingsCalculator.Tiebreak(1, 3, 1, 11, 9));
        StandingsCalculator.Table table = StandingsCalculator.table(three, results, 3, true, round1);
        assertThat(table.tiebreaks()).extracting(StandingsCalculator.Pairing::round).containsOnly(2);
        assertThat(table.tiebreaks()).hasSize(3);

        List<StandingsCalculator.Tiebreak> both = new java.util.ArrayList<>(round1);
        both.add(new StandingsCalculator.Tiebreak(2, 1, 2, 11, 3));
        both.add(new StandingsCalculator.Tiebreak(2, 1, 3, 11, 3));
        both.add(new StandingsCalculator.Tiebreak(2, 2, 3, 11, 3));
        table = StandingsCalculator.table(three, results, 3, true, both);
        assertThat(table.tiebreaks()).isEmpty();
        assertThat(byId(table.rows()).get(1L).place()).isEqualTo(1);
        assertThat(byId(table.rows()).get(2L).place()).isEqualTo(2);
        assertThat(byId(table.rows()).get(3L).place()).isEqualTo(3);
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
