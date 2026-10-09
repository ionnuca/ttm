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
 * plus două turnee: unul în desfășurare și unul cu înscrierea deschisă.
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
        log.info("Date demonstrative încărcate: 10 jucători, 2 turnee și utilizatorul 'jucator' (parola 'jucator123')");
    }

    /**
     * Un turneu comercial în desfășurare, cu câteva rezultate, și unul cu înscrierea deschisă.
     * Operațiile trec prin serviciu, ca administrator, la fel ca din interfață.
     */
    private void createDemoTournaments() {
        var previous = SecurityContextHolder.getContext().getAuthentication();
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(
                "admin", null, AuthorityUtils.createAuthorityList("ROLE_ADMIN")));
        try {
            List<Player> players = playerRepository.findAllByOrderByRatingDescLastNameAscFirstNameAsc();

            TournamentForm autumn = new TournamentForm();
            autumn.setName("Cupa de toamnă");
            autumn.setDate(LocalDate.now().minusDays(1));
            autumn.setBestOf(5);
            autumn.setCommercial(true);
            autumn.setWinnersCount(3);
            autumn.setEntryFee(new BigDecimal("100"));
            Long autumnId = tournamentService.create(autumn).getId();
            players.subList(0, 6).forEach(p -> tournamentService.addParticipant(autumnId, p.getId()));
            tournamentService.start(autumnId);
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

            TournamentForm sunday = new TournamentForm();
            sunday.setName("Turneul de duminică");
            sunday.setDate(LocalDate.now().plusDays(3));
            sunday.setBestOf(3);
            Long sundayId = tournamentService.create(sunday).getId();
            players.subList(1, 5).forEach(p -> tournamentService.addParticipant(sundayId, p.getId()));
        } finally {
            SecurityContextHolder.getContext().setAuthentication(previous);
        }
    }

    private Player save(String firstName, String lastName, PlayStyle style, PlayHand hand, String city, String phone,
                        int rating, int wins, int losses, String blade, String forehand, String backhand) {
        Player player = new Player(firstName, lastName);
        player.setPlayStyle(style);
        player.setPlayHand(hand);
        player.setBlade(blade);
        player.setForehandRubber(forehand);
        player.setBackhandRubber(backhand);
        player.setCity(city);
        player.setPhone(phone);
        player.setRating(rating);
        player.setWins(wins);
        player.setLosses(losses);
        return playerRepository.save(player);
    }
}
