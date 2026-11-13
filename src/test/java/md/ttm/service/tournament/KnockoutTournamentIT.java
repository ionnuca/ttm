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
        KnockoutView view = knockout.knockouts().get(0);
        // câștigătorii de grupă primii (P1, P2), apoi locurile 2 (P3, P4)
        assertThat(names(view.members())).containsExactly("P1", "P2", "P3", "P4");
        assertThat(view.matches()).containsOnlyKeys("W1-1", "W1-2");
        assertThat(pair(view, "W1-1")).isEqualTo("P1-P4");
        assertThat(pair(view, "W1-2")).isEqualTo("P2-P3");

        win(id, "W1-1", "P1");
        win(id, "W1-2", "P2");
        assertThat(details(id).knockouts().get(0).matches()).containsOnlyKeys("W1-1", "W1-2", "W2-1", Bracket.THIRD_PLACE);
        // învinșii din semifinale joacă pentru locul 3
        assertThat(pair(details(id).knockouts().get(0), Bracket.THIRD_PLACE)).isEqualTo("P4-P3");
        win(id, "W2-1", "P1");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, Bracket.THIRD_PLACE, "P3");
        // Tabloul 1 s-a terminat, dar Tabloul 2 încă nu
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);

        // ceilalți (locurile 3 și 4 din grupe) joacă Tabloul 2, organizat la fel
        KnockoutView second = details(id).knockouts().get(1);
        assertThat(second.group().getName()).isEqualTo("Tabloul 2");
        assertThat(details(id).knockouts().get(0).group().getName()).isEqualTo("Tabloul 1");
        assertThat(names(second.members())).containsExactly("P5", "P6", "P7", "P8");
        assertThat(pair(second, "W1-1")).isEqualTo("P5-P8");
        win2(id, "W1-1", "P5");
        win2(id, "W1-2", "P6");
        win2(id, "W2-1", "P5");
        win2(id, Bracket.THIRD_PLACE, "P7");

        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        Map<Integer, List<String>> places = places(done.knockouts().get(0));
        assertThat(places.get(1)).containsExactly("P1");
        assertThat(places.get(2)).containsExactly("P2");
        assertThat(places.get(3)).containsExactly("P3");
        assertThat(places.get(4)).containsExactly("P4");

        // 8 × 100 = 800: câștigătorul Tabloului 2 primește taxa (100), restul de 700 se împarte în Tabloul 1
        assertThat(done.prizes()).extracting(PrizePlace::title)
                .containsExactly("Tabloul 1 · locul 1", "Tabloul 1 · locul 2", "Tabloul 1 · locul 3", "Tabloul 2 · locul 1");
        assertThat(done.prizes()).extracting(PrizePlace::winnerName)
                .containsExactly("Test P1", "Test P2", "Test P3", "Test P5");
        assertThat(done.prizes().get(3).amount()).isEqualByComparingTo("100");

        Podium p5 = tournamentService.podiums().get(done.participants().stream()
                .filter(p -> p.getPlayer().getFirstName().equals("P5")).findFirst().orElseThrow().getPlayer().getId());
        assertThat(p5).isEqualTo(new Podium(0, 0, 0, 1));
        assertThat(done.prizes()).noneMatch(PrizePlace::shared);
    }

    @Test
    void ramuraDeJos_finalaRamuriiDeJosPentruLocul3_corecturi() {
        Long id = tournamentWith(4);
        tournamentService.start(id, TournamentSettings.groupsKnockout(5, 1));
        playGroupFavourites(id);
        tournamentService.startKnockout(id, 4, KnockoutType.DOUBLE);
        // toți au intrat în tablou: nu există Tabloul 2
        assertThat(details(id).knockouts()).hasSize(1);

        win(id, "W1-1", "P1");
        win(id, "W1-2", "P2");
        // învinșii din primul tur trec în ramura de jos, a cărei finală e pentru locul 3
        assertThat(pair(details(id).knockouts().get(0), "L1-1")).isEqualTo("P4-P3");
        win(id, "L1-1", "P3");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, "W2-1", "P1");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);

        // un rezultat de la care depind meciuri jucate nu se mai poate schimba
        TournamentMatch first = details(id).knockouts().get(0).matches().get("W1-1");
        assertThatThrownBy(() -> tournamentService.recordResult(first.getId(), MatchResultForm.sets(0, 3)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("nu se mai poate schimba");
        entityManager.clear(); // în aplicație tranzacția se anulează; aici renunțăm la modificarea nesalvată

        // corectura finalei
        win(id, "W2-1", "P2");
        Map<Integer, List<String>> corrected = places(details(id).knockouts().get(0));
        assertThat(corrected.get(1)).containsExactly("P2");
        assertThat(corrected.get(2)).containsExactly("P1");

        // ștergerea rezultatului pentru locul 3 redeschide turneul
        tournamentService.clearResult(details(id).knockouts().get(0).matches().get("L1-1").getId());
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        win(id, "L1-1", "P4");

        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        Map<Integer, List<String>> places = places(done.knockouts().get(0));
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
        KnockoutView view = details(id).knockouts().get(0);
        assertThat(view.state().size()).isEqualTo(8);
        assertThat(view.matches()).hasSize(2);
        assertThat(view.matches().values()).noneMatch(m -> names(List.of(m.getParticipantA(), m.getParticipantB()))
                .stream().anyMatch(n -> n.equals("P1") || n.equals("P2")));
    }

    @Test
    void unSingurJucatorRamas_castigaTabloul2FaraMeciuri() {
        Long id = tournamentWith(5);
        tournamentService.start(id, TournamentSettings.groupsKnockout(5, 2)); // grupe de 3 și 2
        playGroupFavourites(id);
        tournamentService.startKnockout(id, 2, KnockoutType.SINGLE);

        KnockoutView second = details(id).knockouts().get(1);
        assertThat(names(second.members())).containsExactly("P5");
        assertThat(second.matches()).isEmpty();
        assertThat(second.state().complete()).isTrue();

        win(id, "W1-1", "P1");
        win(id, "W1-2", "P2");
        win(id, "W2-1", "P1");
        win(id, Bracket.THIRD_PLACE, "P3");
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
    }

    @Test
    void corectareaUneiGrupeDupaEtapa2_construiesteTablourileDinNou() {
        Long id = tournamentWith(4);
        tournamentService.start(id, TournamentSettings.groupsKnockout(5, 1));
        playGroupFavourites(id);
        tournamentService.startKnockout(id, 2, KnockoutType.SINGLE);
        assertThat(pair(details(id).knockouts().get(0), "W1-1")).isEqualTo("P1-P2");
        win(id, "W1-1", "P1");
        assertThat(details(id).knockouts().get(0).matches().get("W1-1").isPlayed()).isTrue();

        // corectură care nu schimbă clasamentul: tabloul se refac, dar finala jucată (aceeași pereche) rămâne
        TournamentMatch p1p3 = groupMatch(id, "P1", "P3");
        boolean p1First = p1p3.getParticipantA().getPlayer().getFirstName().equals("P1");
        tournamentService.recordResult(p1p3.getId(), p1First ? MatchResultForm.sets(3, 2) : MatchResultForm.sets(2, 3));
        TournamentMatch kept = details(id).knockouts().get(0).matches().get("W1-1");
        assertThat(kept.isPlayed()).isTrue();
        assertThat(kept.getWinner().getPlayer().getFirstName()).isEqualTo("P1");

        // P2 câștigă acum meciul din grupă cu P1: P2 devine primul, tabloul se refac
        TournamentMatch groupMatch = groupMatch(id, "P1", "P2");
        boolean p1IsA = groupMatch.getParticipantA().getPlayer().getFirstName().equals("P1");
        tournamentService.recordResult(groupMatch.getId(), p1IsA ? MatchResultForm.sets(1, 3) : MatchResultForm.sets(3, 1));

        TournamentDetails rebuilt = details(id);
        assertThat(rebuilt.tournament().getStage()).isEqualTo(TournamentStage.KNOCKOUT);
        assertThat(rebuilt.tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        assertThat(rebuilt.tournament().getKnockoutType()).isEqualTo(KnockoutType.SINGLE);
        assertThat(rebuilt.knockouts()).hasSize(2);
        assertThat(names(rebuilt.knockouts().get(0).members())).containsExactly("P2", "P1");
        assertThat(rebuilt.knockouts().get(0).matches().get("W1-1").isPlayed()).isFalse();

        // ștergerea unui rezultat din grupă: etapa 1 nu mai e completă, turneul revine la grupe
        tournamentService.clearResult(groupMatch(id, "P3", "P4").getId());
        TournamentDetails back = details(id);
        assertThat(back.tournament().getStage()).isEqualTo(TournamentStage.GROUPS);
        assertThat(back.knockouts()).isEmpty();
        assertThat(back.canStartFinals()).isFalse();
        playGroupFavourites(id);
        assertThat(details(id).canStartFinals()).isTrue();
    }

    @Test
    void corectareaUneiGrupeDupaFinale_pastreazaRezultateleFinalelorNeschimbate() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));
        playGroupFavourites(id);
        tournamentService.startFinals(id, 2);
        for (TournamentMatch m : new ArrayList<>(details(id).matches())) {
            if (!m.isPlayed()) {
                boolean aStronger = m.getParticipantA().getSeed() < m.getParticipantB().getSeed();
                tournamentService.recordResult(m.getId(), aStronger ? MatchResultForm.sets(3, 0) : MatchResultForm.sets(0, 3));
            }
        }
        assertThat(details(id).tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);

        TournamentMatch groupMatch = details(id).matches().stream()
                .filter(m -> m.getGroup() != null && m.getGroup().isGroupStage())
                .findFirst().orElseThrow();
        boolean aWon = groupMatch.getWinner().getId().equals(groupMatch.getParticipantA().getId());
        tournamentService.recordResult(groupMatch.getId(), aWon ? MatchResultForm.sets(3, 2) : MatchResultForm.sets(2, 3));

        // același câștigător: calificarea nu se schimbă, toate rezultatele finalelor se păstrează
        TournamentDetails rebuilt = details(id);
        assertThat(rebuilt.tournament().getStage()).isEqualTo(TournamentStage.FINALS);
        assertThat(rebuilt.tournament().getQualifiersPerGroup()).isEqualTo(2);
        assertThat(rebuilt.finals()).hasSize(2);
        assertThat(rebuilt.matches().stream()
                .filter(m -> m.getGroup() != null && !m.getGroup().isGroupStage())
                .allMatch(TournamentMatch::isPlayed)).isTrue();
        assertThat(rebuilt.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
    }

    private TournamentMatch groupMatch(Long id, String x, String y) {
        return details(id).matches().stream()
                .filter(m -> m.getGroup() != null && m.getGroup().isGroupStage())
                .filter(m -> {
                    String a = m.getParticipantA().getPlayer().getFirstName();
                    String b = m.getParticipantB().getPlayer().getFirstName();
                    return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
                })
                .findFirst().orElseThrow();
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
        TournamentMatch match = details(id).knockouts().get(0).matches().get(slot);
        assertThat(match).as(slot).isNotNull();
        boolean aWins = match.getParticipantA().getPlayer().getFirstName().equals(winner);
        tournamentService.recordResult(match.getId(), aWins ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
    }

    private void win2(Long id, String slot, String winner) {
        TournamentMatch match = details(id).knockouts().get(1).matches().get(slot);
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
