package md.ttm.user;

import md.ttm.common.BusinessException;
import md.ttm.common.Texts;
import md.ttm.player.Player;
import md.ttm.player.PlayerRepository;
import md.ttm.security.SecurityUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * Gestionarea conturilor de utilizator. Fiecare cont creat din aplicație are și un profil de jucător.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

    public static final int MIN_PASSWORD_LENGTH = 8;

    private final AppUserRepository userRepository;
    private final PlayerRepository playerRepository;
    private final PasswordEncoder passwordEncoder;
    private final boolean registrationEnabled;

    public UserService(AppUserRepository userRepository,
                       PlayerRepository playerRepository,
                       PasswordEncoder passwordEncoder,
                       @Value("${app.registration.enabled:true}") boolean registrationEnabled) {
        this.userRepository = userRepository;
        this.playerRepository = playerRepository;
        this.passwordEncoder = passwordEncoder;
        this.registrationEnabled = registrationEnabled;
    }

    /** Numele de utilizator se compară și se salvează mereu cu litere mici. */
    public static String normalizeUsername(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }

    public boolean isRegistrationEnabled() {
        return registrationEnabled;
    }

    /** Contul utilizatorului autentificat; gol pentru vizitatori. */
    public Optional<AppUser> findCurrentUser() {
        return SecurityUtils.currentUsername().flatMap(userRepository::findByUsername);
    }

    // ------------------------------------------------------------------
    // Înregistrare publică
    // ------------------------------------------------------------------

    /** Creează un cont nou de tip Utilizator, împreună cu profilul de jucător. */
    @Transactional
    public AppUser register(RegistrationForm form) {
        if (!registrationEnabled) {
            throw new BusinessException("Înregistrarea conturilor noi este dezactivată");
        }
        String username = requireAvailableUsername(form.getUsername(), null);
        requireValidPassword(form.getPassword());
        Player player = playerRepository.save(newPlayer(form.getFirstName(), form.getLastName()));
        AppUser user = new AppUser(username, passwordEncoder.encode(form.getPassword()), Role.USER, player);
        return userRepository.save(user);
    }

    // ------------------------------------------------------------------
    // Administrare
    // ------------------------------------------------------------------

    @PreAuthorize("hasRole('ADMIN')")
    public List<AppUser> findAll() {
        return userRepository.findAllByOrderByUsernameAsc();
    }

    @PreAuthorize("hasRole('ADMIN')")
    public Optional<AppUser> findById(Long id) {
        return userRepository.findById(id);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AppUser create(UserForm form) {
        String username = requireAvailableUsername(form.getUsername(), null);
        requireValidPassword(form.getPassword());
        Player player = playerRepository.save(newPlayer(form.getFirstName(), form.getLastName()));
        AppUser user = new AppUser(username, passwordEncoder.encode(form.getPassword()),
                form.getRole() != null ? form.getRole() : Role.USER, player);
        user.setEnabled(form.isEnabled());
        return userRepository.save(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public AppUser update(Long userId, UserForm form) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilizatorul nu mai există"));
        Role newRole = form.getRole() != null ? form.getRole() : user.getRole();
        boolean losesAdminRights = user.isAdmin() && user.isEnabled()
                && (newRole != Role.ADMIN || !form.isEnabled());
        if (losesAdminRights) {
            if (isCurrentUser(user)) {
                throw new BusinessException("Nu vă puteți retrage propriile drepturi de administrator "
                        + "și nici bloca propriul cont");
            }
            requireAnotherActiveAdmin();
        }

        String newUsername = requireAvailableUsername(form.getUsername(), user.getId());
        if (isCurrentUser(user) && !newUsername.equals(user.getUsername())) {
            throw new BusinessException("Nu vă puteți schimba propriul nume de utilizator");
        }
        user.setUsername(newUsername);
        user.setRole(newRole);
        user.setEnabled(form.isEnabled());
        String password = form.getPassword();
        if (password != null && !password.isBlank()) {
            requireValidPassword(password);
            user.setPasswordHash(passwordEncoder.encode(password));
        }
        if (user.getPlayer() != null) {
            Player player = user.getPlayer();
            player.setFirstName(requireName(form.getFirstName(), "prenumele"));
            player.setLastName(requireName(form.getLastName(), "numele"));
            playerRepository.save(player);
        }
        return userRepository.save(user);
    }

    /** Șterge contul și profilul de jucător asociat. */
    @PreAuthorize("hasRole('ADMIN')")
    @Transactional
    public void delete(Long userId) {
        AppUser user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException("Utilizatorul nu mai există"));
        if (isCurrentUser(user)) {
            throw new BusinessException("Nu vă puteți șterge propriul cont");
        }
        if (user.isAdmin() && user.isEnabled()) {
            requireAnotherActiveAdmin();
        }
        Player player = user.getPlayer();
        userRepository.delete(user);
        userRepository.flush();
        if (player != null) {
            playerRepository.delete(player);
        }
    }

    // ------------------------------------------------------------------
    // Contul propriu
    // ------------------------------------------------------------------

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void changeOwnPassword(String currentPassword, String newPassword) {
        AppUser user = findCurrentUser()
                .orElseThrow(() -> new BusinessException("Nu sunteți autentificat"));
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessException("Parola curentă nu este corectă");
        }
        requireValidPassword(newPassword);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
    }

    // ------------------------------------------------------------------

    private String requireAvailableUsername(String rawUsername, Long ownId) {
        String username = normalizeUsername(rawUsername);
        if (username.length() < 3 || username.length() > 50 || !username.matches("[a-z0-9._-]+")) {
            throw new BusinessException("Numele de utilizator trebuie să aibă 3-50 de caractere: "
                    + "litere latine, cifre și . _ -");
        }
        userRepository.findByUsername(username)
                .filter(existing -> !existing.getId().equals(ownId))
                .ifPresent(existing -> {
                    throw new BusinessException("Numele de utilizator „" + username + "” este deja folosit");
                });
        return username;
    }

    private static void requireValidPassword(String password) {
        if (password == null || password.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException("Parola trebuie să aibă cel puțin " + MIN_PASSWORD_LENGTH + " caractere");
        }
    }

    private static String requireName(String value, String fieldName) {
        String trimmed = Texts.trimToNull(value);
        if (trimmed == null) {
            throw new BusinessException("Introduceți " + fieldName);
        }
        return trimmed;
    }

    private static Player newPlayer(String firstName, String lastName) {
        return new Player(requireName(firstName, "prenumele"), requireName(lastName, "numele"));
    }

    private void requireAnotherActiveAdmin() {
        if (userRepository.countByRoleAndEnabledTrue(Role.ADMIN) <= 1) {
            throw new BusinessException("Trebuie să rămână cel puțin un administrator activ");
        }
    }

    private static boolean isCurrentUser(AppUser user) {
        return SecurityUtils.currentUsername().map(user.getUsername()::equals).orElse(false);
    }
}
