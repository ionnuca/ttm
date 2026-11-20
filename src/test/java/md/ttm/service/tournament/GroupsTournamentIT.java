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
import java.util.Map;

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
    void grupeleSeFormeazaPeUrneDupaRating() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));

        TournamentDetails details = details(id);
        assertThat(details.tournament().getStage()).isEqualTo(TournamentStage.GROUPS);
        assertThat(details.groupStage()).hasSize(2);
        // urnele {P1,P2}, {P3,P4}, {P5,P6}, {P7,P8}: câte un jucător din fiecare în fiecare grupă, în ordinea ratingului
        for (GroupView group : details.groupStage()) {
            List<String> members = names(group.members());
            assertThat(members).hasSize(4);
            assertThat(members.get(0)).isIn("P1", "P2");
            assertThat(members.get(1)).isIn("P3", "P4");
            assertThat(members.get(2)).isIn("P5", "P6");
            assertThat(members.get(3)).isIn("P7", "P8");
        }
        assertThat(details.matches()).hasSize(12); // 6 meciuri în fiecare grupă de 4
        assertThat(details.groupStage().get(0).group().getName()).isEqualTo("Grupa A");
    }

    @Test
    void administratorulReimparteGrupeleCatTimpNuSuntRezultate() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));
        assertThat(details(id).canRedrawGroups()).isTrue();

        tournamentService.redrawGroups(id);
        TournamentDetails redrawn = details(id);
        assertThat(redrawn.groupStage()).hasSize(2);
        assertThat(redrawn.matches()).hasSize(12);
        assertThat(redrawn.groupStage()).allSatisfy(g -> {
            List<String> members = names(g.members());
            assertThat(members.get(0)).isIn("P1", "P2");
            assertThat(members.get(3)).isIn("P7", "P8");
        });

        tournamentService.recordResult(redrawn.matches().get(0).getId(), MatchResultForm.sets(3, 0));
        assertThat(details(id).canRedrawGroups()).isFalse();
        assertThatThrownBy(() -> tournamentService.redrawGroups(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("există deja rezultate");
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

        // etapa 1 e blocată pentru jucători (organizatorul o mai poate corecta: vezi KnockoutTournamentIT)
        TournamentMatch groupMatch = finals.groupStage().get(0).matches().get(0);
        org.springframework.security.core.context.SecurityContext saved =
                org.springframework.security.core.context.SecurityContextHolder.getContext();
        try {
            org.springframework.security.core.context.SecurityContext user =
                    org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
            org.springframework.security.core.context.SecurityContextHolder.setContext(user);
            user.setAuthentication(
                    new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(
                            "jucator", "x", java.util.List.of(
                            new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_USER"))));
            assertThatThrownBy(() -> tournamentService.recordResult(groupMatch.getId(), MatchResultForm.sets(3, 0)))
                    .isInstanceOf(RuntimeException.class);
        } finally {
            org.springframework.security.core.context.SecurityContextHolder.setContext(saved);
        }

        playAllFavourites(id);
        // turneul nu se încheie singur: organizatorul confirmă încheierea
        assertThat(tournamentService.findDetails(id).orElseThrow().readyToFinish()).isTrue();
        tournamentService.finishManually(id);
        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);

        // 8 × 100 = 800; Finala 2: 100 (taxa); restul 700 împărțit 60/40 (420 / 280), rotunjit la 50
        assertThat(done.prizePool()).isEqualByComparingTo("800");
        assertThat(done.prizes()).extracting(PrizePlace::title)
                .containsExactly("Finala 1 · locul 1", "Finala 1 · locul 2", "Finala 2 · locul 1");
        assertThat(done.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("300.00"), new BigDecimal("100.00"));
        assertThat(done.prizes()).extracting(PrizePlace::winnerName)
                .containsExactly("Test P1", "Test P2", "Test P5");

        // locurile din Finala 1 și câștigătorul Finalei 2, pentru lista jucătorilor
        Map<String, Podium> podiums = new java.util.HashMap<>();
        done.participants().forEach(p -> podiums.put(p.getPlayer().getFirstName(),
                tournamentService.podiums().getOrDefault(p.getPlayer().getId(), Podium.NONE)));
        assertThat(podiums.get("P1")).isEqualTo(new Podium(1, 0, 0, 0));
        assertThat(podiums.get("P2")).isEqualTo(new Podium(0, 1, 0, 0));
        assertThat(podiums.get("P3")).isEqualTo(new Podium(0, 0, 1, 0));
        assertThat(podiums.get("P5")).isEqualTo(new Podium(0, 0, 0, 1));
        assertThat(podiums.get("P6")).isEqualTo(Podium.NONE);
    }

    @Test
    void laIncheiereaManualaCastigatorulFinalei2PrimesteMereuTaxa() {
        Long id = tournamentWith(8);
        TournamentSettings settings = TournamentSettings.groups(5, 2);
        settings.setCommercial(true);
        settings.setWinnersCount(1);
        settings.setEntryFee(new BigDecimal("50"));
        tournamentService.start(id, settings);

        // fără finale nu are cine primi premiile: încheierea manuală așteaptă etapa 2
        assertThat(details(id).canFinishManually()).isFalse();
        assertThatThrownBy(() -> tournamentService.finishManually(id))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("etapa 2");

        playAllFavourites(id);
        tournamentService.startFinals(id, 2);
        assertThat(details(id).canFinishManually()).isTrue();
        tournamentService.finishManually(id); // în Finala 2 nu s-a jucat niciun meci

        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(done.prizes()).extracting(PrizePlace::title)
                .containsExactly("Finala 1 · locul 1", "Finala 2 · locul 1");
        assertThat(done.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("350.00"), new BigDecimal("50.00"));
        // Finala 2 nu s-a jucat: P5 și P6 au câte o victorie preluată din grupe și nu s-au întâlnit,
        // deci împart locul 1 (premiul se împarte egal)
        assertThat(done.prizes().get(1).winnerName()).isEqualTo("Test P5, Test P6");
        assertThat(done.prizes().get(1).shared()).isTrue();
        assertThat(done.prizes().get(0).winnerName()).isNotNull();
    }

    @Test
    void etapa2_maiMulteFinale_ultimaPrimesteRestul_taxaReturnataInFinaleleAlese() {
        Long id = tournamentWith(8);
        TournamentSettings settings = TournamentSettings.groups(5, 2);
        settings.setCommercial(true);
        settings.setWinnersCount(1);
        settings.setEntryFee(new BigDecimal("100"));
        tournamentService.start(id, settings);
        playAllFavourites(id);

        // grupe de 4: cel mult 4 finale; cu 3 finale cel mult (4-1)/2 = 1 jucător pe finală
        assertThatThrownBy(() -> tournamentService.startFinals(id, new FinalsSettings(5, 1, java.util.Set.of())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tournamentService.startFinals(id, new FinalsSettings(3, 2, java.util.Set.of())))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tournamentService.startFinals(id, new FinalsSettings(3, 1, java.util.Set.of(1))))
                .isInstanceOf(BusinessException.class);

        tournamentService.startFinals(id, new FinalsSettings(3, 1, java.util.Set.of(2, 3)));
        TournamentDetails finals = details(id);
        assertThat(finals.tournament().getFinalCount()).isEqualTo(3);
        assertThat(finals.finals()).extracting(g -> g.group().getName())
                .containsExactly("Finala 1", "Finala 2", "Finala 3");
        // grupa A: P1, P4, P5, P8; grupa B: P2, P3, P6, P7 (locurile după rating)
        assertThat(names(finals.finals().get(0).members())).containsExactly("P1", "P2");
        assertThat(names(finals.finals().get(1).members())).containsExactly("P3", "P4");
        assertThat(names(finals.finals().get(2).members())).containsExactly("P5", "P6", "P7", "P8");
        assertThat(finals.finals()).extracting(g -> g.group().isFeeRefund()).containsExactly(false, true, true);

        // corectarea unui rezultat din grupe reconstruiește etapa 2 cu aceleași setări
        TournamentMatch groupMatch = finals.groupStage().get(0).matches().get(0);
        tournamentService.recordResult(groupMatch.getId(), groupMatch.getSetsA() > groupMatch.getSetsB()
                ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
        TournamentDetails rebuilt = details(id);
        assertThat(rebuilt.finals()).hasSize(3);
        assertThat(rebuilt.finals()).extracting(g -> g.group().isFeeRefund()).containsExactly(false, true, true);

        playAllFavourites(id);
        // turneul nu se încheie singur: organizatorul confirmă încheierea
        assertThat(tournamentService.findDetails(id).orElseThrow().readyToFinish()).isTrue();
        tournamentService.finishManually(id);
        TournamentDetails done = details(id);
        assertThat(done.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        // 8 × 100 = 800; Finala 2 și Finala 3: câte 100; restul 600 la Finala 1
        assertThat(done.prizes()).extracting(PrizePlace::title)
                .containsExactly("Finala 1 · locul 1", "Finala 2 · locul 1", "Finala 3 · locul 1");
        assertThat(done.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("600.00"), new BigDecimal("100.00"), new BigDecimal("100.00"));
        assertThat(done.prizes()).extracting(PrizePlace::winnerName)
                .containsExactly("Test P1", "Test P3", "Test P5");
    }

    @Test
    void turneuNecomercial_egalitateaDinGrupaSeDepartajeazaInainteaEtapei2() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));
        // grupa lui P1; membrii sunt în ordinea ratingului: P1, apoi câte unul din urnele {P3,P4}, {P5,P6}, {P7,P8}
        GroupView group = details(id).groupStage().stream()
                .filter(g -> names(g.members()).contains("P1")).findFirst().orElseThrow();
        List<TournamentParticipant> m = group.members();
        long first = m.get(0).getId(), second = m.get(1).getId(), third = m.get(2).getId(), last = m.get(3).getId();

        // cerc: primul îl bate pe al doilea, al doilea pe al treilea, al treilea pe primul; toți trei îl bat pe ultimul
        Map<java.util.Set<Long>, Long> winnerOf = Map.of(
                java.util.Set.of(first, second), first,
                java.util.Set.of(second, third), second,
                java.util.Set.of(third, first), third);
        for (TournamentMatch match : group.matches()) {
            long a = match.getParticipantA().getId();
            long b = match.getParticipantB().getId();
            long winner = winnerOf.getOrDefault(java.util.Set.of(a, b), a == last ? b : a);
            tournamentService.recordResult(match.getId(), winner == a ? MatchResultForm.sets(3, 0) : MatchResultForm.sets(0, 3));
        }
        playAllFavourites(id);

        // egalitate perfectă pe locul 1: la un turneu necomercial, grupa cere seturi de departajare
        TournamentDetails tied = details(id);
        GroupView tiedGroup = tied.groupStage().stream()
                .filter(g -> g.group().getId().equals(group.group().getId())).findFirst().orElseThrow();
        assertThat(tiedGroup.pendingTiebreaks()).hasSize(3);
        assertThat(tied.canStartFinals()).isFalse();
        assertThatThrownBy(() -> tournamentService.startFinals(id, 1))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("seturi de departajare");

        // primul câștigă ambele seturi, al doilea îl bate pe al treilea
        for (StandingsCalculator.Pairing p : tiedGroup.pendingTiebreaks()) {
            java.util.Set<Long> pair = java.util.Set.of(p.a(), p.b());
            long winner = pair.contains(first) ? first : second;
            long loser = p.a() == winner ? p.b() : p.a();
            tournamentService.recordTiebreak(id, group.group().getId(), p.round(), winner, loser, 11, 5);
        }

        TournamentDetails decided = details(id);
        assertThat(decided.groupStage()).allSatisfy(g ->
                assertThat(g.standings()).noneMatch(StandingsCalculator.Row::sharedPlace));
        assertThat(decided.canStartFinals()).isTrue();
        tournamentService.startFinals(id, 1);
        assertThat(names(details(id).finals().get(0).members())).containsExactly("P1", "P2");
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
