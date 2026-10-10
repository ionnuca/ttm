package md.ttm.service.tournament;

import md.ttm.IntegrationTest;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.player.PlayerService;
import md.ttm.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Drepturile rolului „Manager de turnee”. */
@IntegrationTest
class TournamentManagerIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerService playerService;
    @Autowired
    UserService userService;
    @Autowired
    PlayerRepository playerRepository;

    @Test
    @WithMockUser(username = "manager", roles = "TOURNAMENT_MANAGER")
    void managerulConduceTurneulDeLaCreareLaIncheiere() {
        Tournament tournament = tournamentService.create(
                new TournamentForm("Cupa managerului", LocalDate.of(2026, 11, 1)));
        List<Player> players = List.of(player("Ana", 1300), player("Ion", 1200), player("Dan", 1100));
        players.forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));
        assertThat(tournamentService.findDetails(tournament.getId()).orElseThrow().manager()).isTrue();

        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(3));
        List<TournamentMatch> matches = tournamentService.findDetails(tournament.getId()).orElseThrow().matches();
        matches.forEach(m -> tournamentService.recordResult(m.getId(), MatchResultForm.sets(2, 0)));

        TournamentDetails finished = tournamentService.findDetails(tournament.getId()).orElseThrow();
        assertThat(finished.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(finished.canRecordResults()).isTrue();

        // Corectarea unui rezultat după încheiere e permisă managerului
        tournamentService.recordResult(matches.get(0).getId(), MatchResultForm.sets(1, 2));
        assertThat(tournamentService.findDetails(tournament.getId()).orElseThrow().tournament().getStatus())
                .isEqualTo(TournamentStatus.FINISHED);
    }

    @Test
    @WithMockUser(username = "manager", roles = "TOURNAMENT_MANAGER")
    void managerulNuAdministreazaJucatoriiSiUtilizatorii() {
        assertThatThrownBy(() -> playerService.save(new Player("Nou", "Jucător")))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> userService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void utilizatorulObisnuitNuPoateCreaTurnee() {
        assertThatThrownBy(() -> tournamentService.create(
                new TournamentForm("Turneu", LocalDate.of(2026, 11, 1))))
                .isInstanceOf(AccessDeniedException.class);
    }

    private Player player(String firstName, int rating) {
        Player player = new Player(firstName, "Manager");
        player.setInitialRating(rating);
        player.setRating(rating);
        return playerRepository.save(player);
    }
}
