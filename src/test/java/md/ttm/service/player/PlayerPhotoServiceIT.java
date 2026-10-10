package md.ttm.service.player;

import jakarta.persistence.EntityManager;
import md.ttm.IntegrationTest;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.model.player.PlayerPhoto;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.service.user.RegistrationForm;
import md.ttm.service.user.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.test.context.support.WithMockUser;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@IntegrationTest
class PlayerPhotoServiceIT {

    @Autowired
    PlayerPhotoService photoService;
    @Autowired
    UserService userService;
    @Autowired
    AppUserRepository userRepository;
    @Autowired
    PlayerRepository playerRepository;
    @Autowired
    EntityManager entityManager;

    @Test
    @WithMockUser(username = "poza", roles = "USER")
    void jucatorulIsiSeteazaPozaDecupataSiMicsorata() throws IOException {
        userService.register(new RegistrationForm("Ana", "Poza", "poza", "parola-poza"));
        entityManager.flush();
        Long own = userRepository.findByUsername("poza").orElseThrow().getPlayer().getId();
        Player other = playerRepository.save(new Player("Ion", "Altul"));

        assertThat(photoService.canEdit(own)).isTrue();
        assertThat(photoService.canEdit(other.getId())).isFalse();
        photoService.save(own, png(800, 600));

        PlayerPhoto photo = photoService.find(own).orElseThrow();
        assertThat(photo.getContentType()).isEqualTo("image/jpeg");
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(photo.getContent()));
        assertThat(stored.getWidth()).isEqualTo(PlayerPhotoService.SIZE);
        assertThat(stored.getHeight()).isEqualTo(PlayerPhotoService.SIZE);
        assertThat(photoService.versions()).containsKey(own);

        assertThatThrownBy(() -> photoService.save(other.getId(), png(100, 100)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> photoService.save(own, "nu e o imagine".getBytes()))
                .isInstanceOf(BusinessException.class);

        photoService.delete(own);
        assertThat(photoService.find(own)).isEmpty();
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    void administratorulSchimbaPozaOricui() throws IOException {
        Player player = playerRepository.save(new Player("Dan", "Mic"));
        photoService.save(player.getId(), png(120, 200));
        BufferedImage stored = ImageIO.read(new ByteArrayInputStream(photoService.find(player.getId()).orElseThrow().getContent()));
        assertThat(stored.getWidth()).isEqualTo(120);
        assertThat(stored.getHeight()).isEqualTo(120);
    }

    private static byte[] png(int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
