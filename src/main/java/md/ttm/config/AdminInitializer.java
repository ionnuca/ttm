package md.ttm.config;

import md.ttm.model.player.Player;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.user.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;

/**
 * La prima pornire creează contul de administrator, dacă nu există încă niciun administrator activ.
 * <p>
 * Parola se ia din {@code APP_ADMIN_PASSWORD}. Dacă lipsește, se generează una aleatorie,
 * afișată o singură dată în log; schimbați-o apoi din pagina "Profilul meu".
 * <p>
 * Dacă sunt setate {@code APP_ADMIN_FIRST_NAME} și {@code APP_ADMIN_LAST_NAME}, administratorul primește
 * și profil de jucător cu acest nume (apare în clasament și se poate înscrie la turnee).
 */
@Component
@Order(1)
public class AdminInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminInitializer.class);
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";

    private final AppUserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminUsername;
    private final String adminPassword;
    private final String adminFirstName;
    private final String adminLastName;

    public AdminInitializer(AppUserRepository userRepository,
                            PlayerRepository playerRepository,
                            PasswordEncoder passwordEncoder,
                            @Value("${app.admin.username:admin}") String adminUsername,
                            @Value("${app.admin.password:}") String adminPassword,
                            @Value("${app.admin.first-name:}") String adminFirstName,
                            @Value("${app.admin.last-name:}") String adminLastName) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.adminFirstName = adminFirstName == null ? "" : adminFirstName.trim();
        this.adminLastName = adminLastName == null ? "" : adminLastName.trim();
        this.passwordEncoder = passwordEncoder;
        this.adminUsername = UserService.normalizeUsername(adminUsername);
        this.adminPassword = adminPassword;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) > 0) {
            return;
        }
        if (userRepository.existsByUsername(adminUsername)) {
            log.warn("Nu există niciun administrator activ, dar utilizatorul '{}' există deja. "
                    + "Activați-l sau acordați-i rolul ADMIN direct în baza de date.", adminUsername);
            return;
        }

        String password = adminPassword;
        if (password == null || password.isBlank()) {
            password = randomPassword();
            log.warn("""

                    ==============================================================
                     Cont de administrator creat
                       utilizator: {}
                       parolă:     {}
                     Parola e afișată o singură dată. Schimbați-o după prima autentificare.
                    ==============================================================""",
                    adminUsername, password);
        } else {
            log.info("Cont de administrator creat: '{}'", adminUsername);
        }
        Player player = null;
        if (!adminFirstName.isEmpty() && !adminLastName.isEmpty()) {
            player = playerRepository.save(new Player(adminFirstName, adminLastName));
        }
        userRepository.save(new AppUser(adminUsername, passwordEncoder.encode(password), Role.ADMIN, player));
    }

    private static String randomPassword() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(16);
        for (int i = 0; i < 16; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }
}
