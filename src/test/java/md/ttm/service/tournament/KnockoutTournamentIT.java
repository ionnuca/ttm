package md.ttm.service.tournament;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.KnockoutType;
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
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class KnockoutTournamentIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void olimpic_dupaGrupe_tablouSiPremii() {
        Long id = tournamentWith(8);
        TournamentSettings settings = TournamentSettings.groupsKnockout(5, 2);
        settings.setCommercial(true);
        settings.setWinnersCount(3);
        settings.setEntryFee(new BigDecimal("100"));
        tournamentService.start(id, settings);

        assertThatThrownBy(() -> tournamentService.startFinals(id, 2))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tournamentService.startKnockout(id, 2, KnockoutType.SINGLE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nu s-a terminat");

        playGroupFavourites(id);
        assertThat(details(id).canStartFinals()).isTrue();
        tournamentService.startKnockout(id, 2, KnockoutType.SINGLE);

        TournamentDetails knockout = details(id);
        assertThat(knockout.tournament().getStage()).isEqualTo(TournamentStage.KNOCKOUT);
        assertThat(knockout.groupStage()).hasSize(2);
        KnockoutView view = knockout.knockout();
        // câștigătorii de grupă primii (P1, P2), apoi locurile 2 (P3, P4)
        assertThat(names(view.members())).containsExactly("P1", "P2", "P3", "P4");
        assertThat(view.matches()).containsOnlyKeys("W1-1", "W1-2");
        assertThat(pair(view, "W1-1")).isEqualTo("P1-P4");
        assertThat(pair(view, "W1-2")).isEqualTo("P2-P3");

        win(id, "W1-1", "P1");
        win(id, "W1-2", "P2");
        assertThat(details(id).knockout().matches()).containsOnlyKeys("W1-1", "W1-2", "W2-1", Bracket.THIRD_PLACE);
        // învinșii din semifinale joacă pentru locul 3
        assertThat(pair(details(id).knockout(), Bracket.THIRD_PLACE)).isEqualTo("P4-P3");
        win(id, "W2-1", "P1");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, Bracket.THIRD_PLACE, "P3");

        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        Map<Integer, List<String>> places = places(done.knockout());
        assertThat(places.get(1)).containsExactly("P1");
        assertThat(places.get(2)).containsExactly("P2");
        assertThat(places.get(3)).containsExactly("P3");
        assertThat(places.get(4)).containsExactly("P4");

        assertThat(done.prizes()).extracting(PrizePlace::winnerName)
                .containsExactly("Test P1", "Test P2", "Test P3");
        assertThat(done.prizes()).noneMatch(PrizePlace::shared);
    }

    @Test
    void ramuraDeJos_finalaRamuriiDeJosPentruLocul3_corecturi() {
        Long id = tournamentWith(4);
        tournamentService.start(id, TournamentSettings.groupsKnockout(5, 1));
        playGroupFavourites(id);
        tournamentService.startKnockout(id, 4, KnockoutType.DOUBLE);

        win(id, "W1-1", "P1");
        win(id, "W1-2", "P2");
        // învinșii din primul tur trec în ramura de jos, a cărei finală e pentru locul 3
        assertThat(pair(details(id).knockout(), "L1-1")).isEqualTo("P4-P3");
        win(id, "L1-1", "P3");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, "W2-1", "P1");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);

        // un rezultat de la care depind meciuri jucate nu se mai poate schimba
        TournamentMatch first = details(id).knockout().matches().get("W1-1");
        assertThatThrownBy(() -> tournamentService.recordResult(first.getId(), MatchResultForm.sets(0, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nu se mai poate schimba");
        entityManager.clear(); // în aplicație tranzacția se anulează; aici renunțăm la modificarea nesalvată

        // corectura finalei
        win(id, "W2-1", "P2");
        Map<Integer, List<String>> corrected = places(details(id).knockout());
        assertThat(corrected.get(1)).containsExactly("P2");
        assertThat(corrected.get(2)).containsExactly("P1");

        // ștergerea rezultatului pentru locul 3 redeschide turneul
        tournamentService.clearResult(details(id).knockout().matches().get("L1-1").getId());
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, "L1-1", "P4");

        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        Map<Integer, List<String>> places = places(done.knockout());
        assertThat(places.get(1)).containsExactly("P2");
        assertThat(places.get(2)).containsExactly("P1");
        assertThat(places.get(3)).containsExactly("P4");
        assertThat(places.get(4)).containsExactly("P3");
    }

    @Test
    void tablouCuLocuriLibere() {
        Long id = tournamentWith(6);
        tournamentService.start(id, TournamentSettings.groupsKnockout(5, 2));
        playGroupFavourites(id);
        assertThatThrownBy(() -> tournamentService.startKnockout(id, 4, KnockoutType.SINGLE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("între 1 și 3");
        tournamentService.startKnockout(id, 3, KnockoutType.SINGLE);

        // 6 jucători într-un tablou de 8: primii doi trec direct în semifinale
        KnockoutView view = details(id).knockout();
        assertThat(view.state().size()).isEqualTo(8);
        assertThat(view.matches()).hasSize(2);
        assertThat(view.matches().values()).noneMatch(m -> names(List.of(m.getParticipantA(), m.getParticipantB()))
                .stream().anyMatch(n -> n.equals("P1") || n.equals("P2")));
    }

    // ------------------------------------------------------------------

    /** Câștigă mereu jucătorul cu ratingul mai mare (P1 > P2 > …). */
    private void playGroupFavourites(Long id) {
        for (TournamentMatch m : new ArrayList<>(details(id).matches())) {
            if (m.isPlayed()) {
                continue;
            }
            boolean aStronger = m.getParticipantA().getSeed() < m.getParticipantB().getSeed();
            tournamentService.recordResult(m.getId(), aStronger ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
        }
    }

    private void win(Long id, String slot, String winner) {
        TournamentMatch match = details(id).knockout().matches().get(slot);
        assertThat(match).as(slot).isNotNull();
        boolean aWins = match.getParticipantA().getPlayer().getFirstName().equals(winner);
        tournamentService.recordResult(match.getId(), aWins ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
    }

    private static String pair(KnockoutView view, String slot) {
        TournamentMatch m = view.matches().get(slot);
        return m.getParticipantA().getPlayer().getFirstName() + "-" + m.getParticipantB().getPlayer().getFirstName();
    }

    private static Map<Integer, List<String>> places(KnockoutView view) {
        return view.state().placings().stream().collect(Collectors.groupingBy(Bracket.Placing::place,
                Collectors.mapping(p -> view.participant(p.participantId()).getPlayer().getFirstName(), Collectors.toList())));
    }

    private Long tournamentWith(int players) {
        Long id = tournamentService.create(new TournamentForm("Eliminare", LocalDate.of(2026, 3, 1))).getId();
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
        entityManager.clear();
        return tournamentService.findDetails(id).orElseThrow();
    }

    private static List<String> names(List<TournamentParticipant> participants) {
        return participants.stream().map(p -> p.getPlayer().getFirstName()).toList();
    }
}
