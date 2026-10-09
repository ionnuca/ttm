package md.ttm.service.tournament;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.player.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.test.context.support.WithMockUser;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class TournamentServiceIT {

    @Autowired
    TournamentService tournamentService;
    @Autowired
    PlayerService playerService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComplet_inscriere_incepere_rezultate_clasament() {
        Tournament tournament = tournamentService.create(form("Cupa de toamnă", false));
        List<Player> players = List.of(
                player("Ana", "Ciobanu", 1200), player("Ion", "Popescu", 1500),
                player("Mihai", "Rusu", 1300), player("Elena", "Moraru", 1100));
        players.forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));

        assertThatThrownBy(() -> tournamentService.addParticipant(tournament.getId(), players.get(0).getId()))
                .isInstanceOf(BusinessException.class);

        tournamentService.start(tournament.getId());
        TournamentDetails details = tournamentService.findDetails(tournament.getId()).orElseThrow();

        // grupa e ordonată după rating, descrescător
        assertThat(details.participants()).extracting(p -> p.getPlayer().getLastName())
                .containsExactly("Popescu", "Rusu", "Ciobanu", "Moraru");
        assertThat(details.participants()).extracting(TournamentParticipant::getSeedRating)
                .containsExactly(1500, 1300, 1200, 1100);
        assertThat(details.tournament().getStatus()).isEqualTo(TournamentStatus.IN_PROGRESS);
        assertThat(details.matches()).hasSize(6);

        // după începere nu se mai poate înscrie nimeni
        Player late = player("Victor", "Lungu", 1000);
        assertThatThrownBy(() -> tournamentService.addParticipant(tournament.getId(), late.getId()))
                .isInstanceOf(BusinessException.class);

        // scor invalid la „best of 5”
        TournamentMatch first = details.matches().get(0);
        assertThatThrownBy(() -> tournamentService.recordResult(first.getId(), MatchResultForm.sets(3, 3)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tournamentService.recordResult(first.getId(), MatchResultForm.sets(2, 1)))
                .isInstanceOf(BusinessException.class);

        // toate meciurile: câștigă mereu cel cu ratingul mai mare; unul prin victorie tehnică
        List<TournamentMatch> matches = details.matches();
        for (int i = 0; i < matches.size(); i++) {
            TournamentMatch m = matches.get(i);
            boolean aIsStronger = m.getParticipantA().getSeed() < m.getParticipantB().getSeed();
            if (i == matches.size() - 1) {
                long winner = aIsStronger ? m.getParticipantA().getId() : m.getParticipantB().getId();
                tournamentService.recordResult(m.getId(), MatchResultForm.walkover(winner));
            } else {
                tournamentService.recordResult(m.getId(), aIsStronger ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(1, 3));
            }
        }

        details = tournamentService.findDetails(tournament.getId()).orElseThrow();
        assertThat(details.tournament().getStatus()).isEqualTo(TournamentStatus.FINISHED);
        assertThat(details.playedMatches()).isEqualTo(6);
        assertThat(details.standings()).extracting(StandingsCalculator.Row::place).containsExactly(1, 2, 3, 4);
        // 2 puncte pe victorie, 1 pe înfrângere; cel care pierde tehnic primește 0 în loc de 1
        int[] expected = {6, 5, 4, 3};
        TournamentMatch last = details.matches().get(details.matches().size() - 1);
        TournamentParticipant loser = last.getWinner().getId().equals(last.getParticipantA().getId())
                ? last.getParticipantB() : last.getParticipantA();
        expected[loser.getSeed() - 1]--;
        assertThat(details.standings()).extracting(StandingsCalculator.Row::points)
                .containsExactly(expected[0], expected[1], expected[2], expected[3]);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void stergereaRezultatuluiRedeschideTurneulIncheiat() {
        Tournament tournament = tournamentService.create(form("Turneu scurt", false));
        Player a = player("Ana", "Ciobanu", 1200);
        Player b = player("Ion", "Popescu", 1500);
        tournamentService.addParticipant(tournament.getId(), a.getId());
        tournamentService.addParticipant(tournament.getId(), b.getId());
        tournamentService.start(tournament.getId());
        TournamentMatch match = tournamentService.findDetails(tournament.getId()).orElseThrow().matches().get(0);

        tournamentService.recordResult(match.getId(), MatchResultForm.sets(3, 0));
        assertThat(status(tournament)).isEqualTo(TournamentStatus.FINISHED);

        tournamentService.clearResult(match.getId());
        assertThat(status(tournament)).isEqualTo(TournamentStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComercialImparteSumaAcumulata() {
        TournamentForm form = form("Turneu comercial", true);
        form.setWinnersCount(3);
        form.setEntryFee(new BigDecimal("100"));
        Tournament tournament = tournamentService.create(form);
        for (int i = 0; i < 8; i++) {
            tournamentService.addParticipant(tournament.getId(), player("Jucător", "Nr" + i, 1000 + i).getId());
        }

        TournamentDetails details = tournamentService.findDetails(tournament.getId()).orElseThrow();
        assertThat(details.prizePool()).isEqualByComparingTo("800");
        assertThat(details.prizes()).extracting(PrizePlace::percentage).containsExactly(50, 30, 20);
        assertThat(details.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("240.00"), new BigDecimal("160.00"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComercialCereTaxaSiCastigatorii() {
        TournamentForm form = form("Fără taxă", true);
        form.setEntryFee(null);
        assertThatThrownBy(() -> tournamentService.create(form)).isInstanceOf(BusinessException.class);
    }

    @Test
    void utilizatorulSeInscrieSiIntroduceRezultatulDoarDacaParticipa() {
        Player ana = player("Ana", "Ciobanu", 1200);
        Player ion = player("Ion", "Popescu", 1500);
        userRepository.save(new AppUser("ana", "x", Role.USER, ana));
        userRepository.save(new AppUser("ion", "x", Role.USER, ion));
        userRepository.save(new AppUser("strain", "x", Role.USER, player("Mihai", "Rusu", 1300)));

        Long tournamentId = as("admin", true, () -> tournamentService.create(form("Turneu", false)).getId());
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        as("ion", false, () -> tournamentService.registerSelf(tournamentId));

        assertThatThrownBy(() -> as("ana", false, () -> tournamentService.registerSelf(tournamentId)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> as("ana", false, () -> {
            tournamentService.start(tournamentId);
            return null;
        })).isInstanceOf(AccessDeniedException.class);

        as("admin", true, () -> {
            tournamentService.start(tournamentId);
            return null;
        });
        Long matchId = as("ana", false, () -> tournamentService.findDetails(tournamentId).orElseThrow()
                .matches().get(0).getId());

        assertThatThrownBy(() -> as("ana", false, () -> {
            tournamentService.unregisterSelf(tournamentId);
            return null;
        })).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> as("strain", false, () ->
                tournamentService.recordResult(matchId, MatchResultForm.sets(3, 0))))
                .isInstanceOf(BusinessException.class);

        TournamentDetails asAna = as("ana", false, () -> tournamentService.findDetails(tournamentId).orElseThrow());
        assertThat(asAna.canRecordResults()).isTrue();
        assertThat(asAna.ownParticipant()).isNotNull();

        as("ana", false, () -> tournamentService.recordResult(matchId, MatchResultForm.sets(1, 3)));
        TournamentMatch recorded = as("ana", false, () -> tournamentService.findDetails(tournamentId).orElseThrow()
                .matches().get(0));
        assertThat(recorded.getRecordedBy()).isEqualTo("ana");
        assertThat(recorded.getWinner().getPlayer().getLastName()).isEqualTo("Ciobanu");
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void jucatorulCareAJucatIntrUnTurneuInceputNuPoateFiSters() {
        Tournament started = tournamentService.create(form("Început", false));
        Tournament open = tournamentService.create(form("Deschis", false));
        Player a = player("Ana", "Ciobanu", 1200);
        Player b = player("Ion", "Popescu", 1500);
        Player c = player("Mihai", "Rusu", 1300);
        tournamentService.addParticipant(started.getId(), a.getId());
        tournamentService.addParticipant(started.getId(), b.getId());
        tournamentService.start(started.getId());
        tournamentService.addParticipant(open.getId(), c.getId());

        assertThatThrownBy(() -> playerService.delete(a.getId())).isInstanceOf(BusinessException.class);

        playerService.delete(c.getId()); // înscris doar la un turneu neînceput: se poate
        entityManager.flush();
        entityManager.clear();
        assertThat(playerRepository.findById(c.getId())).isEmpty();
        assertThat(tournamentService.findDetails(open.getId()).orElseThrow().participants()).isEmpty();
    }

    // ------------------------------------------------------------------

    private TournamentStatus status(Tournament tournament) {
        entityManager.flush();
        entityManager.clear();
        return tournamentService.findDetails(tournament.getId()).orElseThrow().tournament().getStatus();
    }

    private Player player(String firstName, String lastName, int rating) {
        Player player = new Player(firstName, lastName);
        player.setRating(rating);
        return playerRepository.save(player);
    }

    private static TournamentForm form(String name, boolean commercial) {
        TournamentForm form = new TournamentForm();
        form.setName(name);
        form.setDate(LocalDate.of(2026, 10, 15));
        form.setBestOf(5);
        form.setCommercial(commercial);
        if (commercial) {
            form.setWinnersCount(1);
            form.setEntryFee(new BigDecimal("50"));
        }
        return form;
    }

    /** Rulează codul autentificat ca utilizatorul dat. */
    private static <T> T as(String username, boolean admin, java.util.function.Supplier<T> action) {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = new ArrayList<>(List.of("ROLE_USER"));
        if (admin) {
            roles.add("ROLE_ADMIN");
        }
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                username, null, AuthorityUtils.createAuthorityList(roles.toArray(String[]::new))));
        try {
            return action.get();
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }
}
