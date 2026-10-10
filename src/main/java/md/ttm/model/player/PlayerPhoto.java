package md.ttm.model.player;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.FetchType;
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

    /** Poza întreagă, pentru vizualizarea mărită; {@code null} la pozele încărcate înainte de V8. */
    @Basic(fetch = FetchType.LAZY)
    @Column(name = "full_content")
    private byte[] fullContent;

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

    /** Poza mare, sau miniatura dacă poza a fost încărcată înainte de existența variantei mari. */
    public byte[] getFullContentOrThumbnail() {
        return fullContent != null ? fullContent : content;
    }

    public String getContentType() {
        return contentType;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void replace(byte[] content, byte[] fullContent, String contentType) {
        this.content = content;
        this.fullContent = fullContent;
        this.contentType = contentType;
        this.updatedAt = Instant.now();
    }
}
