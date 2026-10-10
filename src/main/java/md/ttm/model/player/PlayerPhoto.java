package md.ttm.model.player;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Poza de profil a unui jucător, păstrată separat ca listele de jucători să nu încarce imaginile. */
@Entity
@Table(name = "player_photo")
public class PlayerPhoto {

    @Id
    @Column(name = "player_id")
    private Long playerId;

    @Column(name = "content", nullable = false)
    private byte[] content;

    @Column(name = "content_type", nullable = false, length = 50)
    private String contentType;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlayerPhoto() {
    }

    public PlayerPhoto(Long playerId) {
        this.playerId = playerId;
    }

    public Long getPlayerId() {
        return playerId;
    }

    public byte[] getContent() {
        return content;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(byte[] content, String contentType) {
        this.content = content;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }
}
