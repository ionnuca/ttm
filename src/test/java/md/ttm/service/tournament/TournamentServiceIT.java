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
import md.ttm.model.notification.Notification;
import md.ttm.model.notification.NotificationType;
import md.ttm.service.notification.NotificationService;
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
    @Autowired
    NotificationService notificationService;

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComplet_inscriere_incepere_rezultate_clasament() {
        Tournament tournament = tournamentService.create(form("Cupa de toamnă"));
        List<Player> players = List.of(
                player("Ana", "Ciobanu", 1200), player("Ion", "Popescu", 1500),
                player("Mihai", "Rusu", 1300), player("Elena", "Moraru", 1100));
        players.forEach(p -> tournamentService.addParticipant(tournament.getId(), p.getId()));

        assertThatThrownBy(() -> tournamentService.addParticipant(tournament.getId(), players.get(0).getId()))
                .isInstanceOf(BusinessException.class);

        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(5));
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
        Tournament tournament = tournamentService.create(form("Turneu scurt"));
        Player a = player("Ana", "Ciobanu", 1200);
        Player b = player("Ion", "Popescu", 1500);
        tournamentService.addParticipant(tournament.getId(), a.getId());
        tournamentService.addParticipant(tournament.getId(), b.getId());
        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(5));
        TournamentMatch match = tournamentService.findDetails(tournament.getId()).orElseThrow().matches().get(0);

        tournamentService.recordResult(match.getId(), MatchResultForm.sets(3, 0));
        assertThat(status(tournament)).isEqualTo(TournamentStatus.FINISHED);

        tournamentService.clearResult(match.getId());
        assertThat(status(tournament)).isEqualTo(TournamentStatus.IN_PROGRESS);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void turneulComercialImparteSumaAcumulata() {
        Tournament tournament = tournamentService.create(form("Turneu comercial"));
        for (int i = 0; i < 8; i++) {
            tournamentService.addParticipant(tournament.getId(), player("Jucător", "Nr" + i, 1000 + i).getId());
        }
        // înainte de începere configurarea comercială nu e stabilită
        assertThat(tournamentService.findDetails(tournament.getId()).orElseThrow().prizePool()).isNull();

        tournamentService.start(tournament.getId(),
                TournamentSettings.commercial(5, 3, new BigDecimal("100")));
        TournamentDetails details = tournamentService.findDetails(tournament.getId()).orElseThrow();
        assertThat(details.tournament().isCommercial()).isTrue();
        assertThat(details.prizePool()).isEqualByComparingTo("800");
        assertThat(details.prizes()).extracting(PrizePlace::percentage).containsExactly(50, 30, 20);
        assertThat(details.prizes()).extracting(PrizePlace::amount)
                .containsExactly(new BigDecimal("400.00"), new BigDecimal("250.00"), new BigDecimal("150.00"));
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void configurareaSeAlegeLaIncepereSiEsteValidata() {
        Tournament tournament = tournamentService.create(form("Fără taxă"));
        tournamentService.addParticipant(tournament.getId(), player("Ana", "Ciobanu", 1200).getId());
        tournamentService.addParticipant(tournament.getId(), player("Ion", "Popescu", 1500).getId());

        assertThatThrownBy(() -> tournamentService.start(tournament.getId(),
                TournamentSettings.commercial(5, 2, null))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> tournamentService.start(tournament.getId(),
                TournamentSettings.roundRobin(4))).isInstanceOf(BusinessException.class);
        assertThat(status(tournament)).isEqualTo(TournamentStatus.REGISTRATION);

        tournamentService.start(tournament.getId(), TournamentSettings.roundRobin(3));
        Tournament started = tournamentService.findDetails(tournament.getId()).orElseThrow().tournament();
        assertThat(started.getBestOf()).isEqualTo(3);
        assertThat(started.getSetsToWin()).isEqualTo(2);
        assertThat(started.isCommercial()).isFalse();
    }

    @Test
    void utilizatorulSeInscrieSiIntroduceRezultatulDoarDacaParticipa() {
        Player ana = player("Ana", "Ciobanu", 1200);
        Player ion = player("Ion", "Popescu", 1500);
        userRepository.save(new AppUser("ana", "x", Role.USER, ana));
        userRepository.save(new AppUser("ion", "x", Role.USER, ion));
        userRepository.save(new AppUser("strain", "x", Role.USER, player("Mihai", "Rusu", 1300)));

        Long tournamentId = as("admin", true, () -> tournamentService.create(form("Turneu")).getId());
        long adminUnread = as("admin", true, () -> notificationService.countUnread());
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        as("ion", false, () -> tournamentService.registerSelf(tournamentId));

        assertThatThrownBy(() -> as("ana", false, () -> tournamentService.registerSelf(tournamentId)))
                .isInstanceOf(BusinessException.class);

        // cererile așteaptă confirmarea; organizatorii primesc câte o notificare
        TournamentDetails requested = as("admin", true, () -> tournamentService.findDetails(tournamentId).orElseThrow());
        assertThat(requested.participants()).isEmpty();
        assertThat(requested.pendingRequests()).hasSize(2);
        assertThat(as("admin", true, () -> notificationService.countUnread())).isEqualTo(adminUnread + 2);
        assertThat(as("ana", false, () -> tournamentService.findDetails(tournamentId).orElseThrow().ownRequestPending())).isTrue();
        assertThatThrownBy(() -> as("ana", false, () -> {
            tournamentService.confirmParticipant(tournamentId, requested.pendingRequests().get(0).getId());
            return null;
        })).isInstanceOf(AccessDeniedException.class);
        as("admin", true, () -> {
            requested.pendingRequests().forEach(p -> tournamentService.confirmParticipant(tournamentId, p.getId()));
            return null;
        });
        assertThat(as("ana", false, () -> notificationService.findRecent()))
                .extracting(Notification::getType).containsExactly(NotificationType.REGISTRATION_CONFIRMED);
        assertThat(as("ana", false, () -> notificationService.countUnread())).isEqualTo(1);
        as("ana", false, () -> {
            notificationService.markAllRead();
            return null;
        });
        assertThat(as("ana", false, () -> notificationService.countUnread())).isZero();
        assertThatThrownBy(() -> as("ana", false, () -> {
            tournamentService.start(tournamentId, TournamentSettings.roundRobin(5));
            return null;
        })).isInstanceOf(AccessDeniedException.class);

        as("admin", true, () -> {
            tournamentService.start(tournamentId, TournamentSettings.roundRobin(5));
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
    void notificarileNuSeRepeta_siSePotSterge() {
        Player ana = player("Ana", "Ciobanu", 1200);
        userRepository.save(new AppUser("ana", "x", Role.USER, ana));
        Long tournamentId = as("admin", true, () -> tournamentService.create(form("Turneu")).getId());

        // cerere, retragere, cerere din nou: organizatorul vede o singură cerere
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        as("ana", false, () -> {
            tournamentService.unregisterSelf(tournamentId);
            return null;
        });
        assertThat(requestsFor(tournamentId)).isZero();
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        as("ana", false, () -> {
            tournamentService.unregisterSelf(tournamentId);
            return null;
        });
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        assertThat(requestsFor(tournamentId)).isEqualTo(1);

        // respinsă, apoi cerută și confirmată: jucătorul vede doar starea curentă
        Long request = as("admin", true, () -> tournamentService.findDetails(tournamentId).orElseThrow()
                .pendingRequests().get(0).getId());
        as("admin", true, () -> {
            tournamentService.rejectParticipant(tournamentId, request);
            return null;
        });
        assertThat(requestsFor(tournamentId)).isZero();
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        Long again = as("admin", true, () -> tournamentService.findDetails(tournamentId).orElseThrow()
                .pendingRequests().get(0).getId());
        as("admin", true, () -> {
            tournamentService.confirmParticipant(tournamentId, again);
            return null;
        });
        List<Notification> anaNotifications = as("ana", false, () -> notificationService.findRecent());
        assertThat(anaNotifications).extracting(Notification::getType)
                .containsExactly(NotificationType.REGISTRATION_CONFIRMED);
        assertThat(requestsFor(tournamentId)).isZero();

        // ștergerea unei notificări
        as("ana", false, () -> {
            notificationService.delete(anaNotifications.get(0).getId());
            return null;
        });
        assertThat(as("ana", false, () -> notificationService.findRecent())).isEmpty();
    }

    /** Câte cereri de înscriere la turneul dat vede administratorul în notificări. */
    private long requestsFor(Long tournamentId) {
        return as("admin", true, () -> notificationService.findRecent()).stream()
                .filter(n -> n.getType() == NotificationType.REGISTRATION_REQUESTED && tournamentId.equals(n.getTournamentId()))
                .count();
    }

    @Test
    void cerereaRespinsaSauNeconfirmataNuIntraInTurneu() {
        Player ana = player("Ana", "Ciobanu", 1200);
        Player ion = player("Ion", "Popescu", 1500);
        Player dan = player("Dan", "Rusu", 1300);
        Player eva = player("Eva", "Lungu", 1100);
        userRepository.save(new AppUser("ana", "x", Role.USER, ana));
        userRepository.save(new AppUser("ion", "x", Role.USER, ion));
        userRepository.save(new AppUser("dan", "x", Role.USER, dan));

        Long tournamentId = as("admin", true, () -> tournamentService.create(form("Turneu")).getId());
        as("ana", false, () -> tournamentService.registerSelf(tournamentId));
        as("ion", false, () -> tournamentService.registerSelf(tournamentId));
        as("dan", false, () -> tournamentService.registerSelf(tournamentId));
        TournamentDetails requested = as("admin", true, () -> tournamentService.findDetails(tournamentId).orElseThrow());
        Long anaRequest = requested.pendingRequests().stream()
                .filter(p -> p.getPlayer().getId().equals(ana.getId())).findFirst().orElseThrow().getId();

        as("admin", true, () -> {
            tournamentService.rejectParticipant(tournamentId, anaRequest);
            // adăugarea de către organizator confirmă cererea existentă
            tournamentService.addParticipant(tournamentId, ion.getId());
            tournamentService.addParticipant(tournamentId, eva.getId());
            tournamentService.start(tournamentId, TournamentSettings.roundRobin(5));
            return null;
        });

        TournamentDetails started = as("admin", true, () -> tournamentService.findDetails(tournamentId).orElseThrow());
        assertThat(started.participants()).extracting(p -> p.getPlayer().getFirstName()).containsExactlyInAnyOrder("Ion", "Eva");
        assertThat(as("ana", false, () -> notificationService.findRecent()))
                .extracting(Notification::getType).containsExactly(NotificationType.REGISTRATION_REJECTED);
        assertThat(as("ion", false, () -> notificationService.findRecent()))
                .extracting(Notification::getType).containsExactly(NotificationType.REGISTRATION_CONFIRMED);
        assertThat(as("dan", false, () -> notificationService.findRecent()))
                .extracting(Notification::getType).containsExactly(NotificationType.REGISTRATION_EXPIRED);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void jucatorulCareAJucatIntrUnTurneuInceputNuPoateFiSters() {
        Tournament started = tournamentService.create(form("Început"));
        Tournament open = tournamentService.create(form("Deschis"));
        Player a = player("Ana", "Ciobanu", 1200);
        Player b = player("Ion", "Popescu", 1500);
        Player c = player("Mihai", "Rusu", 1300);
        tournamentService.addParticipant(started.getId(), a.getId());
        tournamentService.addParticipant(started.getId(), b.getId());
        tournamentService.start(started.getId(), TournamentSettings.roundRobin(5));
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
        player.setInitialRating(rating);
        player.setRating(rating);
        return playerRepository.save(player);
    }

    private static TournamentForm form(String name) {
        return new TournamentForm(name, LocalDate.of(2026, 10, 15));
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
