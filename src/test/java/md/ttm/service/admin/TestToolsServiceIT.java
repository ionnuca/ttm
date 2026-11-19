package md.ttm.service.admin;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.tournament.TournamentDetails;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.service.tournament.TournamentService;
import md.ttm.service.tournament.TournamentSettings;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class TestToolsServiceIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    EntityManager entityManager;
    @Autowired
    TestToolsService configured;

    private TestToolsService tools() {
        return new TestToolsService(true, playerRepository, tournamentService);
    }

    @Test
    void inProductieSuntOprite() {
        assertThat(configured.enabled()).isFalse();
        assertThatThrownBy(() -> configured.createRandomPlayers(5)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> configured.fillResults(1L)).isInstanceOf(BusinessException.class);
    }

    @Test
    void jucatoriAleatori() {
        long before = playerRepository.count();
        assertThat(tools().createRandomPlayers(50)).isEqualTo(50);
        assertThat(playerRepository.count()).isEqualTo(before + 50);
        assertThat(playerRepository.findAll()).allSatisfy(p -> assertThat(p.getRating()).isBetween(0, 3000));
    }

    @Test
    void grupe_rezultateleSiDepartajarileSeCompleteaza() {
        Long id = tournamentWith(9);
        tournamentService.start(id, TournamentSettings.twoStages(5, 2));
        int filled = tools().fillResults(id);

        TournamentDetails details = details(id);
        assertThat(filled).isGreaterThanOrEqualTo(16); // grupe de 5 și 4: 10 + 6 meciuri
        assertThat(details.unplayedMatches()).isZero();
        assertThat(details.pendingTiebreakCount()).isZero();
        assertThat(details.canStartFinals()).isTrue();
    }

    @Test
    void tablou_seCompleteazaPanaLaCapat() {
        Long id = tournamentWith(6);
        tournamentService.start(id, TournamentSettings.knockout(5, KnockoutType.DOUBLE));
        tools().fillResults(id);

        TournamentDetails details = details(id);
        assertThat(details.readyToFinish()).isTrue();
        assertThat(details.knockouts().get(0).state().complete()).isTrue();
    }

    private Long tournamentWith(int players) {
        Long id = tournamentService.create(new TournamentForm("Unelte", LocalDate.of(2026, 3, 1))).getId();
        for (int i = 1; i <= players; i++) {
            Player p = new Player("P" + i, "Test");
            p.setInitialRating(1500 - i * 10);
            p.setRating(1500 - i * 10);
            tournamentService.addParticipant(id, playerRepository.save(p).getId());
        }
        return id;
    }

    private TournamentDetails details(Long id) {
        entityManager.flush();
        entityManager.clear();
        return tournamentService.findDetails(id).orElseThrow();
    }
}
