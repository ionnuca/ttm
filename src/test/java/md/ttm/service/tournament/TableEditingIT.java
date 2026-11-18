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

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Adăugarea și scoaterea jucătorilor din tabele după începerea turneului. */
@IntegrationTest
@WithMockUser(username = "admin", roles = "ADMIN")
class TableEditingIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    void roundRobin_adaugaSiScoateJucatori() {
        Long id = tournamentWith(4);
        tournamentService.start(id, TournamentSettings.roundRobin(5));
        assertThat(details(id).matches()).hasSize(6);
        TournamentMatch first = details(id).matches().get(0);
        tournamentService.recordResult(first.getId(), MatchResultForm.sets(3, 1));

        Long p5 = newPlayer("P5", 1500);
        assertThat(tournamentService.addPlayersToTable(id, null, List.of(p5))).isEqualTo(1);

        TournamentDetails added = details(id);
        assertThat(added.participants()).hasSize(5);
        assertThat(added.matches()).hasSize(10);
        assertThat(added.standings()).hasSize(5);
        assertNoPlayerTwiceInARound(added.matches());
        assertThat(added.matches().stream().filter(TournamentMatch::isPlayed)).hasSize(1);

        assertThatThrownBy(() -> tournamentService.addPlayersToTable(id, null, List.of(p5)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deja înscris");

        // scoaterea unui jucător care are un rezultat: meciurile lui dispar, inclusiv cel jucat
        TournamentParticipant played = first.getParticipantA();
        tournamentService.removePlayerFromTable(id, null, played.getId());
        TournamentDetails removed = details(id);
        assertThat(removed.participants()).hasSize(4);
        assertThat(removed.matches()).hasSize(6).noneMatch(TournamentMatch::isPlayed);
    }

    @Test
    void grupe_adaugareInGrupaSiFinala_scoatere() {
        Long id = tournamentWith(8);
        tournamentService.start(id, TournamentSettings.groups(5, 2));
        GroupView groupA = details(id).groupStage().get(0);
        assertThat(groupA.members()).hasSize(4);

        Long p9 = newPlayer("P9", 1500);
        tournamentService.addPlayersToTable(id, groupA.group().getId(), List.of(p9));
        GroupView grown = details(id).groupStage().get(0);
        assertThat(grown.members()).hasSize(5);
        assertThat(grown.matches()).hasSize(10);
        assertNoPlayerTwiceInARound(grown.matches());
        assertThat(details(id).groupStage().get(1).members()).hasSize(4);

        // scos din grupă: iese din turneu
        TournamentParticipant out = grown.members().get(1);
        tournamentService.removePlayerFromTable(id, groupA.group().getId(), out.getId());
        assertThat(details(id).groupStage().get(0).members()).hasSize(4);
        assertThat(details(id).participants()).extracting(p -> p.getId()).doesNotContain(out.getId());

        // în etapa grupelor nu se poate umbla la finale, iar după etapa 2 grupele nu se mai pot edita
        playAll(id);
        tournamentService.startFinals(id, 2);
        Long p10 = newPlayer("P10", 1400);
        assertThatThrownBy(() -> tournamentService.addPlayersToTable(id, groupA.group().getId(), List.of(p10)))
                .isInstanceOf(BusinessException.class);

        GroupView final1 = details(id).finals().get(0);
        int before = final1.members().size();
        tournamentService.addPlayersToTable(id, final1.group().getId(), List.of(p10));
        GroupView finalGrown = details(id).finals().get(0);
        assertThat(finalGrown.members()).hasSize(before + 1);
        long withP10 = finalGrown.matches().stream()
                .filter(m -> m.getParticipantA().getPlayer().getFirstName().equals("P10")
                        || m.getParticipantB().getPlayer().getFirstName().equals("P10"))
                .count();
        assertThat(withP10).isEqualTo(before);

        // scos din finală: rămâne în turneu
        TournamentParticipant fromFinal = finalGrown.members().get(0);
        tournamentService.removePlayerFromTable(id, final1.group().getId(), fromFinal.getId());
        TournamentDetails after = details(id);
        assertThat(after.finals().get(0).members()).hasSize(before);
        assertThat(after.participants()).extracting(p -> p.getId()).contains(fromFinal.getId());
        assertThat(after.tournament().getStage()).isEqualTo(TournamentStage.FINALS);
        assertThat(after.tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);

        // ... și poate fi pus înapoi în finală: apare printre jucătorii care se pot adăuga
        Long removedPlayer = fromFinal.getPlayer().getId();
        assertThat(tournamentService.findPlayersForTable(id, final1.group().getId()))
                .extracting(p -> p.getId()).contains(removedPlayer);
        assertThat(tournamentService.findPlayersForTable(id, after.finals().get(1).group().getId()))
                .extracting(p -> p.getId()).contains(removedPlayer);
        tournamentService.addPlayersToTable(id, final1.group().getId(), List.of(removedPlayer));
        GroupView back = details(id).finals().get(0);
        assertThat(back.members()).extracting(p -> p.getId()).contains(fromFinal.getId());
        assertThat(details(id).participants()).filteredOn(p -> p.getPlayer().getId().equals(removedPlayer)).hasSize(1);
        // cu colegul din grupă nu se joacă din nou: rezultatul din etapa 1 se preia
        long againstGroupMate = back.matches().stream()
                .filter(m -> m.getParticipantA().getId().equals(fromFinal.getId()) || m.getParticipantB().getId().equals(fromFinal.getId()))
                .count();
        assertThat(againstGroupMate).isEqualTo(back.members().size() - 2);
        assertThat(back.carried()).anyMatch(m -> m.getParticipantA().getId().equals(fromFinal.getId())
                || m.getParticipantB().getId().equals(fromFinal.getId()));
        assertNoPlayerTwiceInARound(back.matches());
        // e deja într-o finală: nu se mai poate adăuga încă o dată
        assertThatThrownBy(() -> tournamentService.addPlayersToTable(id, details(id).finals().get(1).group().getId(),
                List.of(removedPlayer))).isInstanceOf(BusinessException.class);
    }

    // ------------------------------------------------------------------

    private static void assertNoPlayerTwiceInARound(List<TournamentMatch> matches) {
        Map<Integer, List<TournamentMatch>> byRound = matches.stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRoundNo));
        byRound.forEach((round, list) -> {
            Set<Long> seen = new HashSet<>();
            for (TournamentMatch m : list) {
                assertThat(seen.add(m.getParticipantA().getId())).as("turul %d", round).isTrue();
                assertThat(seen.add(m.getParticipantB().getId())).as("turul %d", round).isTrue();
            }
        });
    }

    private void playAll(Long id) {
        for (TournamentMatch m : new ArrayList<>(details(id).matches())) {
            if (!m.isPlayed()) {
                boolean aStronger = m.getParticipantA().getSeed() < m.getParticipantB().getSeed();
                tournamentService.recordResult(m.getId(), aStronger ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
            }
        }
    }

    private Long newPlayer(String name, int rating) {
        Player p = new Player(name, "Test");
        p.setInitialRating(rating);
        p.setRating(rating);
        return playerRepository.save(p).getId();
    }

    private Long tournamentWith(int players) {
        Long id = tournamentService.create(new TournamentForm("Editare", LocalDate.of(2026, 3, 1))).getId();
        for (int i = 1; i <= players; i++) {
            tournamentService.addParticipant(id, newPlayer("P" + i, 2000 - i * 10));
        }
        return id;
    }

    private TournamentDetails details(Long id) {
        entityManager.flush();
        entityManager.clear();
        return tournamentService.findDetails(id).orElseThrow();
    }
}
