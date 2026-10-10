package md.ttm.service.tournament;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStage;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class GroupsTournamentIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void grupeleSeFormeazaInSerpuialaDupaRating() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));

        TournamentDetails details = details(id);
        assertThat(details.tournament().getStage()).isEqualTo(TournamentStage.GROUPS);
        assertThat(details.groupStage()).hasSize(2);
        assertThat(names(details.groupStage().get(0).members())).containsExactly("P1", "P4", "P5", "P8");
        assertThat(names(details.groupStage().get(1).members())).containsExactly("P2", "P3", "P6", "P7");
        assertThat(details.matches()).hasSize(12); // 6 meciuri în fiecare grupă de 4
        assertThat(details.groupStage().get(0).group().getName()).isEqualTo("Grupa A");
    }

    @Test
    void validareaNumaruluiDeGrupe() {
        Long id = tournamentWith(5);
        assertThatThrownBy(() -> tournamentService.start(id, TournamentSettings.groups(5, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("cel puțin 6 participanți");
    }

    @Test
    void etapa2_finaleCuRezultatePreluate_blocareaEtapei1_premii() {
        Long id = tournamentWith(8);
        TournamentSettings settings = TournamentSettings.groups(5, 2);
        settings.setCommercial(true);
        settings.setWinnersCount(2);
        settings.setEntryFee(new BigDecimal("100"));
        tournamentService.start(id, settings);

        assertThatThrownBy(() -> tournamentService.startFinals(id, 2))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nu s-a terminat");

        playAllFavourites(id);
        TournamentDetails afterGroups = details(id);
        assertThat(afterGroups.tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        assertThat(afterGroups.canStartFinals()).isTrue();

        assertThatThrownBy(() -> tournamentService.startFinals(id, 4)).isInstanceOf(BusinessException.class);
        tournamentService.startFinals(id, 2);

        TournamentDetails finals = details(id);
        assertThat(finals.tournament().getStage()).isEqualTo(TournamentStage.FINALS);
        GroupView final1 = finals.finals().get(0);
        GroupView final2 = finals.finals().get(1);
        assertThat(final1.group().getName()).isEqualTo("Finala 1");
        // câștigătorii de grupă primii, apoi locurile 2, fiecare nivel după rating
        assertThat(names(final1.members())).containsExactly("P1", "P2", "P3", "P4");
        assertThat(names(final2.members())).containsExactly("P5", "P6", "P7", "P8");
        // P1-P4 și P2-P3 s-au întâlnit în grupe: se preiau, nu se mai joacă
        assertThat(final1.matches()).hasSize(4);
        assertThat(final1.carried()).hasSize(2);
        assertThat(final1.standings()).extracting(StandingsCalculator.Row::played).containsOnly(1);

        // etapa 1 e blocată
        TournamentMatch groupMatch = finals.groupStage().get(0).matches().get(0);
        assertThatThrownBy(() -> tournamentService.recordResult(groupMatch.getId(), MatchResultForm.sets(3, 0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("etapei 1");

        playAllFavourites(id);
        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);

        // 8 × 100 = 800; Finala 2: 100 (taxa); restul 700 împărțit 60/40 între primii doi din Finala 1
        assertThat(done.prizePool()).isEqualByComparingTo("800");
        assertThat(done.prizes()).extracting(PrizePlace::title)
                .containsExactly("Finala 1 · locul 1", "Finala 1 · locul 2", "Finala 2 · locul 1");
        assertThat(done.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("420.00"), new BigDecimal("280.00"), new BigDecimal("100.00"));
        assertThat(done.prizes()).extracting(PrizePlace::winnerName)
                .containsExactly("Test P1", "Test P2", "Test P5");
    }

    // ------------------------------------------------------------------

    /** Câștigă mereu jucătorul cu ratingul mai mare (P1 > P2 > …). */
    private void playAllFavourites(Long id) {
        for (TournamentMatch m : new ArrayList<>(details(id).matches())) {
            if (m.isPlayed()) {
                continue;
            }
            boolean aStronger = m.getParticipantA().getSeed() < m.getParticipantB().getSeed();
            tournamentService.recordResult(m.getId(), aStronger ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
        }
    }

    private Long tournamentWith(int players) {
        Long id = tournamentService.create(new TournamentForm("Grupe", LocalDate.of(2026, 3, 1))).getId();
        for (int i = 1; i <= players; i++) {
            Player p = new Player("P" + i, "Test");
            p.setInitialRating(2000 - i * 10);
            p.setRating(2000 - i * 10);
            tournamentService.addParticipant(id, playerRepository.save(p).getId());
        }
        return id;
    }

    private TournamentDetails details(Long id) {
        entityManager.flush();
        return tournamentService.findDetails(id).orElseThrow();
    }

    private static List<String> names(List<TournamentParticipant> participants) {
        return participants.stream().map(p -> p.getPlayer().getFirstName()).toList();
    }
}
