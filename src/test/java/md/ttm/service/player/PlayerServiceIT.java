package md.ttm.service.player;

import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.PlayHand;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.user.RegistrationForm;
import md.ttm.service.user.UserService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.test.context.support.WithMockUser;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PlayerServiceIT {

    @Autowired
    PlayerService playerService;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    UserService userService;
    @Autowired
    EntityManager entityManager;

    @BeforeEach
    void cleanPlayers() {
        userRepository.findAll().stream()
                .filter(u -> u.getPlayer() != null)
                .forEach(userRepository::delete);
        userRepository.flush();
        playerRepository.deleteAll();
        playerRepository.flush();
    }

    @Test
    void clasamentulEsteOrdonatDupaRatingCuLocuriEgalePentruRatingEgal() {
        playerRepository.save(player("Ana", "Ciobanu", 1100));
        playerRepository.save(player("Ion", "Popescu", 1300));
        playerRepository.save(player("Mihai", "Rusu", 1100));
        playerRepository.save(player("Elena", "Moraru", 900));

        List<RankedPlayer> ranked = playerService.findRanked();

        assertThat(ranked).extracting(rp -> rp.player().getLastName())
                .containsExactly("Popescu", "Ciobanu", "Rusu", "Moraru");
        assertThat(ranked).extracting(RankedPlayer::rank)
                .containsExactly(1, 2, 2, 4);
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorulPoateAdaugaEditaSiStergeJucatori() {
        Player created = playerService.save(player("  Ion ", "Popescu", 1000));
        assertThat(created.getPlayHand()).isNull();
        assertThat(created.hasEquipment()).isFalse();
        created.setPlayHand(PlayHand.RIGHT);
        created.setBlade("Stiga Allround Classic");
        created.setCity("   ");
        created.setPhone("+373 69 123 456");
        Player saved = playerService.save(created);

        assertThat(saved.getFirstName()).isEqualTo("Ion");
        assertThat(saved.getCity()).isNull();
        assertThat(saved.getPhone()).isEqualTo("+373 69 123 456");
        assertThat(saved.getPlayHand()).isEqualTo(PlayHand.RIGHT);
        assertThat(saved.getBlade()).isEqualTo("Stiga Allround Classic");

        playerService.delete(saved.getId());
        assertThat(playerRepository.findById(saved.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void stergereaJucatoruluiStergeSiContulAsociat() {
        Long userId = userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "parola-ana")).getId();
        Long playerId = userRepository.findById(userId).orElseThrow().getPlayer().getId();

        playerService.delete(playerId);
        entityManager.flush();
        entityManager.clear();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(playerRepository.findById(playerId)).isEmpty();
    }

    @Test
    void vizitatorulNuPoateModificaJucatori() {
        assertThatThrownBy(() -> playerService.save(player("Ion", "Popescu", 1000)))
                .isInstanceOf(AuthenticationException.class);
    }

    @Test
    @WithMockUser(username = "oricine", roles = "USER")
    void utilizatorulObisnuitNuPoateStergeJucatori() {
        Player player = playerRepository.save(player("Ion", "Popescu", 1000));
        assertThatThrownBy(() -> playerService.delete(player.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void jucatorulIsiPoateEditaProfilulDarNuRatingul() {
        userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "parola-ana"));
        entityManager.flush();
        entityManager.clear();
        Player own = userRepository.findByUsername("ana").orElseThrow().getPlayer();
        entityManager.clear();

        own.setCity("Bălți");
        own.setPlayStyle(PlayStyle.DEFENCE);
        own.setPlayHand(PlayHand.LEFT);
        own.setBlade("  Butterfly Viscaria ");
        own.setForehandRubber("Tenergy 05");
        own.setBackhandRubber("");
        own.setRating(3000);
        own.setWins(99);
        Player saved = playerService.updateOwnProfile(own);

        assertThat(saved.getCity()).isEqualTo("Bălți");
        assertThat(saved.getPlayStyle()).isEqualTo(PlayStyle.DEFENCE);
        assertThat(saved.getPlayHand()).isEqualTo(PlayHand.LEFT);
        assertThat(saved.getBlade()).isEqualTo("Butterfly Viscaria");
        assertThat(saved.getForehandRubber()).isEqualTo("Tenergy 05");
        assertThat(saved.getBackhandRubber()).isNull();
        assertThat(saved.getRating()).isEqualTo(Player.DEFAULT_RATING);
        assertThat(saved.getWins()).isZero();
    }

    @Test
    @WithMockUser(username = "ana", roles = "USER")
    void jucatorulNuPoateEditaProfilulAltuia() {
        userService.register(new RegistrationForm("Ana", "Ciobanu", "ana", "parola-ana"));
        Player other = playerRepository.save(player("Ion", "Popescu", 1000));
        entityManager.flush();
        entityManager.clear();

        assertThatThrownBy(() -> playerService.updateOwnProfile(other))
                .isInstanceOf(BusinessException.class);
    }

    private static Player player(String firstName, String lastName, int rating) {
        Player player = new Player(firstName, lastName);
        player.setRating(rating);
        return player;
    }
}
