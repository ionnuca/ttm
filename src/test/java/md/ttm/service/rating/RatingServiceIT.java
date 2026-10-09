package md.ttm.service.rating;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.rating.RatingHistory;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.player.PlayerService;
import md.ttm.service.tournament.MatchResultForm;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.service.tournament.TournamentService;
import md.ttm.service.tournament.TournamentSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class RatingServiceIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    RatingService ratingService;
    @Autowired
    PlayerService playerService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void turneulIncheiatModificaRatingulSiStatisticile() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        Long t = tournament("T1", 1, ana, ion);

        TournamentMatch match = onlyMatch(t);
        tournamentService.recordResult(match.getId(), winnerIs(match, ana, 3, 1));

        assertThat(rating(ana)).isEqualTo(1020);
        assertThat(rating(ion)).isEqualTo(980);
        assertThat(reload(ana).getWins()).isEqualTo(1);
        assertThat(reload(ion).getLosses()).isEqualTo(1);

        List<RatingHistory> history = ratingService.findHistory(ana.getId());
        assertThat(history).hasSize(1);
        assertThat(history.get(0).getRatingBefore()).isEqualTo(1000);
        assertThat(history.get(0).getRatingAfter()).isEqualTo(1020);
        assertThat(history.get(0).getKFactor()).isEqualTo(40);
    }

    @Test
    void victoriaTehnicaNuModificaRatingulDarConteazaLaStatistici() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1100);
        Long t = tournament("T1", 1, ana, ion);

        TournamentMatch match = onlyMatch(t);
        long anaParticipant = match.getParticipantA().getPlayer().getId().equals(ana.getId())
                ? match.getParticipantA().getId() : match.getParticipantB().getId();
        tournamentService.recordResult(match.getId(), MatchResultForm.walkover(anaParticipant));

        assertThat(rating(ana)).isEqualTo(1000);
        assertThat(rating(ion)).isEqualTo(1100);
        assertThat(reload(ana).getWins()).isEqualTo(1);
        assertThat(onlyMatch(t).getRatingDeltaA()).isNull();
    }

    @Test
    void corectareaUnuiRezultatRecalculeazaInLantTurneeleUrmatoare() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        Long first = tournament("Primul", 1, ana, ion);
        TournamentMatch m1 = onlyMatch(first);
        tournamentService.recordResult(m1.getId(), winnerIs(m1, ana, 3, 0));   // Ana 1020, Ion 980

        Long second = tournament("Al doilea", 10, ana, ion);
        TournamentMatch m2 = onlyMatch(second);
        tournamentService.recordResult(m2.getId(), winnerIs(m2, ion, 3, 2));   // din 1020/980: Ion +22
        assertThat(rating(ana)).isEqualTo(998);
        assertThat(rating(ion)).isEqualTo(1002);

        // administratorul corectează primul turneu (încheiat): de fapt a câștigat Ion
        tournamentService.recordResult(m1.getId(), winnerIs(m1, ion, 3, 0));   // Ion 1020, Ana 980
        // al doilea turneu, din 980/1020: Ion favorit, câștigă +18
        assertThat(rating(ion)).isEqualTo(1038);
        assertThat(rating(ana)).isEqualTo(962);
    }

    @Test
    void stergereaUnuiRezultatScoateTurneulDinCalcul() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        Long t = tournament("T1", 1, ana, ion);
        TournamentMatch match = onlyMatch(t);
        tournamentService.recordResult(match.getId(), winnerIs(match, ana, 3, 1));
        assertThat(rating(ana)).isEqualTo(1020);

        tournamentService.clearResult(match.getId());
        assertThat(rating(ana)).isEqualTo(1000);
        assertThat(rating(ion)).isEqualTo(1000);
        assertThat(ratingService.findHistory(ana.getId())).isEmpty();
    }

    @Test
    void stergereaUnuiTurneuIncheiatScoateTurneulDinCalcul() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        Long t = tournament("T1", 1, ana, ion);
        TournamentMatch match = onlyMatch(t);
        tournamentService.recordResult(match.getId(), winnerIs(match, ana, 3, 1));

        tournamentService.delete(t);
        assertThat(rating(ana)).isEqualTo(1000);
        assertThat(reload(ana).getWins()).isZero();
    }

    @Test
    void modificareaRatinguluiInitialRecalculeazaRatingul() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        Long t = tournament("T1", 1, ana, ion);
        TournamentMatch match = onlyMatch(t);
        tournamentService.recordResult(match.getId(), winnerIs(match, ana, 3, 1));

        Player edited = reload(ana);
        entityManager.detach(edited);
        edited.setInitialRating(1200);
        playerService.save(edited);
        // 1200 vs 1000: Ana câștigă, E = 0.76 -> +40 × 0.24 = +9.6 -> +10
        assertThat(rating(ana)).isEqualTo(1210);
        assertThat(rating(ion)).isEqualTo(990);
    }

    @Test
    void doarAdministratorulPoateModificaRezultateleUnuiTurneuIncheiat() {
        Player ana = player("Ana", 1000);
        Player ion = player("Ion", 1000);
        userRepository.save(new AppUser("ana", "x", Role.USER, ana));
        Long t = tournament("T1", 1, ana, ion);
        TournamentMatch match = onlyMatch(t);
        tournamentService.recordResult(match.getId(), winnerIs(match, ana, 3, 1));

        var admin = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "ana", null, AuthorityUtils.createAuthorityList("ROLE_USER")));
        try {
            assertThatThrownBy(() -> tournamentService.recordResult(match.getId(), winnerIs(match, ion, 3, 0)))
                    .isInstanceOf(BusinessException.class)
                    .hasMessageContaining("administrator");
        } finally {
            SecurityContextHolder.getContext().setAuthentication(admin);
        }
        assertThat(rating(ana)).isEqualTo(1020);
    }

    // ------------------------------------------------------------------

    private Player player(String name, int initialRating) {
        Player player = new Player(name, "Test");
        player.setInitialRating(initialRating);
        player.setRating(initialRating);
        return playerRepository.save(player);
    }

    private Long tournament(String name, int day, Player... players) {
        Long id = tournamentService.create(new TournamentForm(name, LocalDate.of(2026, 1, day))).getId();
        for (Player p : players) {
            tournamentService.addParticipant(id, p.getId());
        }
        tournamentService.start(id, TournamentSettings.roundRobin(5));
        return id;
    }

    private TournamentMatch onlyMatch(Long tournamentId) {
        return tournamentService.findDetails(tournamentId).orElseThrow().matches().get(0);
    }

    private static MatchResultForm winnerIs(TournamentMatch match, Player winner, int winnerSets, int loserSets) {
        boolean aWins = match.getParticipantA().getPlayer().getId().equals(winner.getId());
        return aWins ? MatchResultForm.sets(winnerSets, loserSets) : MatchResultForm.sets(loserSets, winnerSets);
    }

    private Player reload(Player player) {
        entityManager.flush();
        return playerRepository.findById(player.getId()).orElseThrow();
    }

    private int rating(Player player) {
        return reload(player).getRating();
    }
}
