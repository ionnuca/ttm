package md.ttm.config;

import md.ttm.player.PlayStyle;
import md.ttm.player.Player;
import md.ttm.player.PlayerRepository;
import md.ttm.user.AppUser;
import md.ttm.user.AppUserRepository;
import md.ttm.user.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Date demonstrative pentru dezvoltare (profilul "demo", inclus automat în profilul "dev").
 * Se încarcă doar dacă nu există încă niciun jucător.
 * <p>
 * Creează și un cont de test: utilizator {@code jucator}, parolă {@code jucator123}.
 */
@Component
@Profile("demo")
@Order(2)
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DemoDataInitializer(PlayerRepository playerRepository,
                               AppUserRepository userRepository,
                               PasswordEncoder passwordEncoder) {
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
        Player first = save("Ion", "Popescu", PlayStyle.ATTACK, "Chișinău", "+373 69 123 456", 1420, 18, 4);
        save("Mihai", "Rusu", PlayStyle.DEFENCE, "Bălți", "+373 79 222 333", 1385, 15, 6);
        save("Ana", "Ciobanu", PlayStyle.ATTACK, "Chișinău", "+373 68 555 010", 1350, 14, 7);
        save("Victor", "Lungu", PlayStyle.ATTACK, "Orhei", null, 1350, 12, 8);
        save("Elena", "Moraru", PlayStyle.DEFENCE, "Cahul", "+373 60 777 888", 1290, 11, 9);
        save("Andrei", "Țurcanu", PlayStyle.ATTACK, "Chișinău", "+373 69 000 111", 1255, 9, 10);
        save("Sergiu", "Bîrcă", PlayStyle.DEFENCE, "Ungheni", null, 1210, 8, 11);
        save("Natalia", "Cojocaru", PlayStyle.ATTACK, "Soroca", "+373 78 444 222", 1180, 7, 12);
        save("Dumitru", "Ceban", PlayStyle.ATTACK, "Comrat", null, 1120, 5, 13);
        save("Cristina", "Sârbu", PlayStyle.DEFENCE, "Chișinău", "+373 67 321 654", 1000, 0, 0);

        AppUser demoUser = new AppUser("jucator", passwordEncoder.encode("jucator123"), Role.USER, first);
        userRepository.save(demoUser);
        log.info("Date demonstrative încărcate: 10 jucători și utilizatorul 'jucator' (parola 'jucator123')");
    }

    private Player save(String firstName, String lastName, PlayStyle style, String city, String phone,
                        int rating, int wins, int losses) {
        Player player = new Player(firstName, lastName);
        player.setPlayStyle(style);
        player.setCity(city);
        player.setPhone(phone);
        player.setRating(rating);
        player.setWins(wins);
        player.setLosses(losses);
        return playerRepository.save(player);
    }
}
