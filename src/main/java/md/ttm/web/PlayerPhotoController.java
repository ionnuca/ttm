package md.ttm.web;

import md.ttm.service.player.PlayerPhotoService;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;

/**
 * Pozele jucătorilor: {@code /foto/jucator/<id>?v=<versiune>}. Versiunea se schimbă la fiecare poză nouă,
 * deci browserul poate păstra imaginea în cache mult timp.
 */
@RestController
public class PlayerPhotoController {

    public static final String PATH = "foto/jucator/";

    private final PlayerPhotoService photoService;

    public PlayerPhotoController(PlayerPhotoService photoService) {
        this.photoService = photoService;
    }

    @GetMapping("/" + PATH + "{playerId}")
    public ResponseEntity<byte[]> photo(@PathVariable Long playerId) {
        return photoService.find(playerId)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(p.getContent()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Poza mare, pentru vizualizare: {@code /foto/jucator/<id>/mare?v=<versiune>}. */
    @GetMapping("/" + PATH + "{playerId}/mare")
    public ResponseEntity<byte[]> fullPhoto(@PathVariable Long playerId) {
        return photoService.find(playerId)
                .map(p -> ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(p.getContentType()))
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(30)).cachePublic())
                        .body(p.getFullContentOrThumbnail()))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Adresa pozei mari; {@code null} dacă jucătorul nu are poză. */
    public static String fullUrl(Long playerId, Long version) {
        return version == null ? null : PATH + playerId + "/mare?v=" + version;
    }

    /** Adresa pozei, relativă la rădăcina aplicației; {@code null} dacă jucătorul nu are poză. */
    public static String url(Long playerId, Long version) {
        return version == null ? null : PATH + playerId + "?v=" + version;
    }
}
