package md.ttm.user;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.player.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class UserServiceIT {

    @Autowired
    UserService userService;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    EntityManager entityManager;

    @Test
    void administratorulInitialEsteCreatLaPornire() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.isEnabled()).isTrue();
        assertThat(passwordEncoder.matches("admin-test-parola", admin.getPasswordHash())).isTrue();
    }

    @Test
    void inregistrareaCreeazaUtilizatorSiJucator() {
        AppUser user = userService.register(new RegistrationForm(" Ana ", "Ciobanu", " Ana.C ", "parola-sigura"));

        assertThat(user.getUsername()).isEqualTo("ana.c");
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getPlayer()).isNotNull();
        assertThat(user.getPlayer().getFirstName()).isEqualTo("Ana");
        assertThat(user.getPlayer().getRating()).isEqualTo(1000);
        assertThat(user.getPasswordHash()).isNotEqualTo("parola-sigura");
        assertThat(passwordEncoder.matches("parola-sigura", user.getPasswordHash())).isTrue();
    }

    @Test
    void numeleDeUtilizatorTrebuieSaFieUnic() {
        userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "parola-sigura"));

        assertThatThrownBy(() -> userService.register(new RegistrationForm("Ana", "Alta", "ANA", "parola-sigura")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deja folosit");
    }

    @Test
    void parolaScurtaEsteRespinsa() {
        assertThatThrownBy(() -> userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "scurta")))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorulPoateCreaEditaSiStergeUtilizatori() {
        UserForm form = new UserForm();
        form.setFirstName("Mihai");
        form.setLastName("Rusu");
        form.setUsername("mihai");
        form.setPassword("parola-mihai");
        form.setRole(Role.USER);
        AppUser created = userService.create(form);
        assertThat(created.getPlayer()).isNotNull();

        UserForm edit = UserForm.from(created);
        edit.setLastName("Rusu-Nou");
        edit.setEnabled(false);
        edit.setPassword("");
        AppUser updated = userService.update(created.getId(), edit);
        assertThat(updated.isEnabled()).isFalse();
        assertThat(updated.getPlayer().getLastName()).isEqualTo("Rusu-Nou");
        assertThat(passwordEncoder.matches("parola-mihai", updated.getPasswordHash())).isTrue();

        Long playerId = updated.getPlayer().getId();
        userService.delete(created.getId());
        entityManager.flush();
        entityManager.clear();
        assertThat(userRepository.findById(created.getId())).isEmpty();
        assertThat(playerRepository.findById(playerId)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorulNuSePoateStergeSauRetrogradaPeSine() {
        AppUser admin = userRepository.findByUsername("admin").orElseThrow();

        assertThatThrownBy(() -> userService.delete(admin.getId()))
                .isInstanceOf(BusinessException.class);

        UserForm demote = UserForm.from(admin);
        demote.setRole(Role.USER);
        assertThatThrownBy(() -> userService.update(admin.getId(), demote))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @WithMockUser(username = "oricine", roles = "USER")
    void utilizatorulObisnuitNuVedeListaDeUtilizatori() {
        assertThatThrownBy(() -> userService.findAll())
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void utilizatorulIsiPoateSchimbaParola() {
        userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "parola-veche"));

        assertThatThrownBy(() -> userService.changeOwnPassword("gresita", "parola-noua"))
                .isInstanceOf(BusinessException.class);

        userService.changeOwnPassword("parola-veche", "parola-noua");
        AppUser ana = userRepository.findByUsername("ana").orElseThrow();
        assertThat(passwordEncoder.matches("parola-noua", ana.getPasswordHash())).isTrue();
    }
}
