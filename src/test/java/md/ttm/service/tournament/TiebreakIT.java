package md.ttm.service.tournament;

import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Egalitățile nedepartajate: loc împărțit la turneul obișnuit, seturi de departajare la cel comercial. */
@IntegrationTest
class TiebreakIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulObisnuit_egalitateaTotalaImparteLocul() {
        Long id = cycle(TournamentSettings.roundRobin(5));
        TournamentDetails details = tournamentService.findDetails(id).orElseThrow();
        assertThat(details.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(details.standings()).extracting(StandingsCalculator.Row::place).containsOnly(1);
        assertThat(details.pendingTiebreaks()).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComercial_seJoacaSeturiDeDepartajare() {
        Long id = cycle(TournamentSettings.commercial(5, 1, new BigDecimal("100")));
        TournamentDetails details = tournamentService.findDetails(id).orElseThrow();
        // toate meciurile jucate, dar egalitatea totală cere seturi de departajare
        assertThat(details.tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        assertThat(details.pendingTiebreaks()).hasSize(3);
        assertThat(details.canFinishManually()).isTrue();

        StandingsCalculator.Pairing first = details.pendingTiebreaks().get(0);
        assertThatThrownBy(() -> tournamentService.recordTiebreak(id, null, 1, first.a(), first.b(), 11, 10))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Scor invalid");
        assertThatThrownBy(() -> tournamentService.recordTiebreak(id, null, 2, first.a(), first.b(), 11, 5))
                .isInstanceOf(BusinessException.class);

        // primul din fiecare pereche câștigă: jucătorul cu seed 1 câștigă ambele seturi
        for (StandingsCalculator.Pairing p : details.pendingTiebreaks()) {
            tournamentService.recordTiebreak(id, null, p.round(), p.a(), p.b(), 11, 7);
        }
        TournamentDetails done = tournamentService.findDetails(id).orElseThrow();
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(done.pendingTiebreaks()).isEmpty();
        assertThat(done.tiebreaks()).hasSize(3);
        assertThat(done.standings()).extracting(StandingsCalculator.Row::place).containsExactly(1, 2, 3);
        assertThat(done.prizes().get(0).winnerName()).isEqualTo("Test Ana");
        assertThat(done.prizes().get(0).shared()).isFalse();
        assertThat(done.ratingChanges()).hasSize(3); // la încheiere ratingul s-a recalculat

        // corectarea unui meci anulează seturile de departajare (egalitățile se pot schimba)
        TournamentMatch match = done.matches().get(0);
        tournamentService.recordResult(match.getId(), MatchResultForm.sets(3, 1));
        assertThat(tournamentService.findDetails(id).orElseThrow().tiebreaks()).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComercialIncheiatManual_premiulLoculuiImpartit() {
        Long id = cycle(TournamentSettings.commercial(5, 1, new BigDecimal("100")));
        tournamentService.finishManually(id);
        TournamentDetails done = tournamentService.findDetails(id).orElseThrow();
        assertThat(done.prizes().get(0).shared()).isTrue();
        assertThat(done.prizes().get(0).winnerName()).contains("Test Ana", "Test Ion", "Test Dan");
    }

    /** Trei jucători cu rating egal, „cerc” perfect: fiecare câștigă un meci cu 3:0. */
    private Long cycle(TournamentSettings settings) {
        Tournament tournament = tournamentService.create(new TournamentForm("Cerc", LocalDate.of(2026, 11, 5)));
        List<Player> players = List.of(player("Ana"), player("Ion"), player("Dan"));
        players.forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));
        tournamentService.start(tournament.getId(), settings);
        List<TournamentMatch> matches = tournamentService.findDetails(tournament.getId()).orElseThrow().matches();
        // cerc: primul îl bate pe al doilea, al doilea pe al treilea, al treilea pe primul
        List<Long> ids = matches.stream()
                .flatMap(m -> java.util.stream.Stream.of(m.getParticipantA().getId(), m.getParticipantB().getId()))
                .distinct().sorted().toList();
        for (TournamentMatch m : matches) {
            int ia = ids.indexOf(m.getParticipantA().getId());
            int ib = ids.indexOf(m.getParticipantB().getId());
            boolean aWins = (ia + 1) % 3 == ib;
            tournamentService.recordResult(m.getId(), aWins ? MatchResultForm.sets(3, 0) : MatchResultForm.sets(0, 3));
        }
        return tournament.getId();
    }

    private Player player(String firstName) {
        Player player = new Player(firstName, "Test");
        player.setInitialRating(1000);
        player.setRating(1000);
        return playerRepository.save(player);
    }
}
