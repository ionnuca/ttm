package md.ttm.config;

import md.ttm.model.player.PlayHand;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.service.tournament.MatchResultForm;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.service.tournament.TournamentService;
import md.ttm.service.tournament.TournamentSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Date demonstrative pentru dezvoltare (profilul "demo", inclus automat în profilul "dev").
 * Se încarcă doar dacă nu există încă niciun jucător.
 * <p>
 * Creează și un cont de test: utilizator {@code jucator}, parolă {@code jucator123},
 * plus patru turnee: unul încheiat, unul în desfășurare, unul „Grupe + finale” cu etapa 1 jucată
 * și unul cu înscrierea deschisă.
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final TournamentService tournamentService;

    public DemoDataInitializer(PlayerRepository playerRepository,
                               AppUserRepository userRepository,
                               PasswordEncoder passwordEncoder,
                               TournamentService tournamentService) {
        this.tournamentService = tournamentService;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (playerRepository.count() > 0) {
            return;
        }
        Player first = save("Ion", "Popescu", PlayStyle.ATTACK, PlayHand.RIGHT, "Chișinău", "+373 69 123 456",
                1420, 18, 4, "Butterfly Viscaria", "Tenergy 05", "Dignics 09C");
        save("Mihai", "Rusu", PlayStyle.DEFENCE, PlayHand.RIGHT, "Bălți", "+373 79 222 333",
                1385, 15, 6, "Butterfly Defence Alpha", "Tenergy 64", "Feint Long III");
        save("Ana", "Ciobanu", PlayStyle.ATTACK, PlayHand.LEFT, "Chișinău", "+373 68 555 010",
                1350, 14, 7, "Stiga Cybershape Carbon", "DNA Dragon Grip", "DNA Platinum XH");
        save("Victor", "Lungu", PlayStyle.ATTACK, PlayHand.RIGHT, "Orhei", null,
                1350, 12, 8, "DHS Hurricane Long 5", "Hurricane 3 Neo", "Tenergy 05");
        save("Elena", "Moraru", PlayStyle.DEFENCE, PlayHand.RIGHT, "Cahul", "+373 60 777 888",
                1290, 11, 9, "Tibhar Defense Plus", "Evolution MX-P", "Grass D.TecS");
        save("Andrei", "Țurcanu", PlayStyle.ATTACK, PlayHand.LEFT, "Chișinău", "+373 69 000 111",
                1255, 9, 10, "Yasaka Ma Lin Extra Offensive", "Rakza 7", "Rakza 7 Soft");
        save("Sergiu", "Bîrcă", PlayStyle.DEFENCE, PlayHand.RIGHT, "Ungheni", null,
                1210, 8, 11, null, null, null);
        save("Natalia", "Cojocaru", PlayStyle.ATTACK, PlayHand.RIGHT, "Soroca", "+373 78 444 222",
                1180, 7, 12, "Butterfly Timo Boll ALC", "Tenergy 80", "Tenergy 80");
        save("Dumitru", "Ceban", PlayStyle.ATTACK, null, "Comrat", null,
                1120, 5, 13, null, null, null);
        save("Cristina", "Sârbu", PlayStyle.DEFENCE, PlayHand.LEFT, "Chișinău", "+373 67 321 654",
                1000, 0, 0, "Donic Defplay Senso", "Bluefire M2", null);

        AppUser demoUser = new AppUser("jucator", passwordEncoder.encode("jucator123"), Role.USER, first);
        userRepository.save(demoUser);
        createDemoTournaments();
        log.info("Date demonstrative încărcate: 10 jucători, 4 turnee și utilizatorul 'jucator' (parola 'jucator123')");
    }

    /**
     * Un turneu încheiat (cu rating calculat), unul comercial în desfășurare, cu câteva rezultate,
     * și unul cu înscrierea deschisă.
     * Operațiile trec prin serviciu, ca administrator, la fel ca din interfață.
     */
    private void createDemoTournaments() {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        try {
            List<Player> players = playerRepository.findAllByOrderByRatingDescLastNameAscFirstNameAsc();

            // Un turneu încheiat (toți jucătorii), ca să existe istoric de rating
            Long summerId = tournamentService.create(
                    new TournamentForm("Cupa de vară", LocalDate.now().minusDays(60))).getId();
            players.forEach(p -> tournamentService.addParticipant(summerId, p.getId()));
            tournamentService.start(summerId, TournamentSettings.roundRobin(5));
            int game = 0;
            for (TournamentMatch match : tournamentService.findDetails(summerId).orElseThrow().matches()) {
                MatchResultForm result;
                if (game == 7) {
                    result = MatchResultForm.walkover(match.getParticipantB().getId());
                } else if (game % 6 == 2) {
                    result = MatchResultForm.sets(2, 3); // surpriză: câștigă jucătorul mai slab
                } else {
                    result = game % 2 == 0 ? MatchResultForm.sets(3, 1) : MatchResultForm.sets(3, 2);
                }
                tournamentService.recordResult(match.getId(), result);
                game++;
            }

            Long autumnId = tournamentService.create(
                    new TournamentForm("Cupa de toamnă", LocalDate.now().minusDays(1))).getId();
            byLastName(players, "Popescu", "Rusu", "Ciobanu", "Lungu", "Moraru", "Țurcanu")
                    .forEach(p -> tournamentService.addParticipant(autumnId, p.getId()));
            tournamentService.start(autumnId, TournamentSettings.commercial(5, 3, new BigDecimal("100")));
            int i = 0;
            for (TournamentMatch match : tournamentService.findDetails(autumnId).orElseThrow().matches()) {
                if (match.getRoundNo() > 3) {
                    continue;
                }
                MatchResultForm result = switch (i % 5) {
                    case 0 -> MatchResultForm.sets(3, 1);
                    case 1 -> MatchResultForm.sets(3, 0);
                    case 2 -> MatchResultForm.sets(2, 3);
                    case 3 -> MatchResultForm.walkover(match.getParticipantA().getId());
                    default -> MatchResultForm.sets(3, 2);
                };
                tournamentService.recordResult(match.getId(), result);
                i++;
            }

            // Turneu „Grupe + finale”: 9 jucători în 3 grupe, etapa 1 încheiată, gata pentru etapa 2
            Long winterId = tournamentService.create(
                    new TournamentForm("Liga de iarnă", LocalDate.now())).getId();
            players.stream().limit(9).forEach(p -> tournamentService.addParticipant(winterId, p.getId()));
            TournamentSettings winterSettings = TournamentSettings.groups(5, 3);
            winterSettings.setCommercial(true);
            winterSettings.setWinnersCount(2);
            winterSettings.setEntryFee(new BigDecimal("50"));
            tournamentService.start(winterId, winterSettings);
            int w = 0;
            for (TournamentMatch match : tournamentService.findDetails(winterId).orElseThrow().matches()) {
                MatchResultForm result = switch (w % 4) {
                    case 0 -> MatchResultForm.sets(3, 1);
                    case 1 -> MatchResultForm.sets(3, 2);
                    case 2 -> MatchResultForm.sets(1, 3); // surpriză
                    default -> MatchResultForm.sets(3, 0);
                };
                tournamentService.recordResult(match.getId(), result);
                w++;
            }

            Long sundayId = tournamentService.create(
                    new TournamentForm("Turneul de duminică", LocalDate.now().plusDays(3))).getId();
            byLastName(players, "Rusu", "Ciobanu", "Lungu", "Moraru")
                    .forEach(p -> tournamentService.addParticipant(sundayId, p.getId()));
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private static List<Player> byLastName(List<Player> players, String... lastNames) {
        List<String> names = List.of(lastNames);
        return players.stream().filter(p -> names.contains(p.getLastName())).toList();
    }

    private Player save(String firstName, String lastName, PlayStyle style, PlayHand hand, String city, String phone,
                        int rating, int unusedWins, int unusedLosses, String blade, String forehand, String backhand) {
        Player player = new Player(firstName, lastName);
        player.setPlayStyle(style);
        player.setPlayHand(hand);
        player.setBlade(blade);
        player.setForehandRubber(forehand);
        player.setBackhandRubber(backhand);
        player.setCity(city);
        player.setPhone(phone);
        player.setInitialRating(rating);
        player.setRating(rating);
        return playerRepository.save(player);
    }
}
