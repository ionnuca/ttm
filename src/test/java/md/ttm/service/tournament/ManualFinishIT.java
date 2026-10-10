package md.ttm.service.tournament;

import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import md.ttm.repository.RatingHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class ManualFinishIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    RatingHistoryRepository historyRepository;

    @Test
    @WithMockUser(username = "manager", roles = "TOURNAMENT_MANAGER")
    void turneulSePoateIncheiaManualCuMeciuriNejucate() {
        Tournament tournament = tournamentService.create(new TournamentForm("Cupa scurtă", LocalDate.of(2026, 11, 2)));
        List<Player> players = List.of(player("Ana", 1300), player("Ion", 1200), player("Dan", 1100), player("Eva", 1000));
        players.forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));
        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(3));
        List<TournamentMatch> matches = tournamentService.findDetails(tournament.getId()).orElseThrow().matches();
        tournamentService.recordResult(matches.get(0).getId(), MatchResultForm.sets(2, 0));
        tournamentService.recordResult(matches.get(1).getId(), MatchResultForm.sets(0, 2));

        assertThat(tournamentService.findDetails(tournament.getId()).orElseThrow().canFinishManually()).isTrue();
        long unplayed = tournamentService.finishManually(tournament.getId());

        TournamentDetails details = tournamentService.findDetails(tournament.getId()).orElseThrow();
        assertThat(unplayed).isEqualTo(4);
        assertThat(details.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(details.unplayedMatches()).isEqualTo(4);
        assertThat(details.canFinishManually()).isFalse();
        // ratingul s-a calculat doar din cele 2 meciuri jucate
        assertThat(historyRepository.findByTournamentId(tournament.getId())).hasSize(4);
        assertThat(details.matches()).filteredOn(TournamentMatch::isPlayed)
                .allSatisfy(m -> assertThat(m.getRatingDeltaA()).isNotNull());
        assertThat(details.matches()).filteredOn(m -> !m.isPlayed())
                .allSatisfy(m -> assertThat(m.getRatingDeltaA()).isNull());

        assertThatThrownBy(() -> tournamentService.finishManually(tournament.getId()))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void utilizatorulObisnuitNuPoateIncheiaTurneul() {
        assertThatThrownBy(() -> tournamentService.finishManually(1L))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Player player(String firstName, int rating) {
        Player player = new Player(firstName, "Manual");
        player.setInitialRating(rating);
        player.setRating(rating);
        return playerRepository.save(player);
    }
}
