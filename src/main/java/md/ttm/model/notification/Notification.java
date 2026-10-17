package md.ttm.model.notification;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.user.AppUser;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.Objects;

/**
 * O notificare pentru un utilizator (clopoțelul din bara de sus). Păstrează numele turneului,
 * ca să rămână lizibilă și dacă turneul e șters.
 */
@Entity
@Table(name = "notification")
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 40)
    private NotificationType type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @Column(name = "tournament_name", nullable = false, length = 150)
    private String tournamentName;

    @Column(name = "player_name", length = 210)
    private String playerName;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "read_at")
    private Instant readAt;

    public Notification() {
    }

    public Notification(AppUser user, NotificationType type, Tournament tournament, String playerName) {
        this.user = user;
        this.type = type;
        this.tournament = tournament;
        this.tournamentName = tournament.getName();
        this.playerName = playerName;
    }

    public boolean isRead() {
        return readAt != null;
    }

    public Long getId() {
        return id;
    }

    public AppUser getUser() {
        return user;
    }

    public NotificationType getType() {
        return type;
    }

    public Tournament getTournament() {
        return tournament;
    }

    /** Id-ul turneului, fără încărcarea lui; {@code null} dacă turneul a fost șters. */
    public Long getTournamentId() {
        return tournament != null ? tournament.getId() : null;
    }

    public String getTournamentName() {
        return tournamentName;
    }

    public String getPlayerName() {
        return playerName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getReadAt() {
        return readAt;
    }

    public void setReadAt(Instant readAt) {
        this.readAt = readAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Notification other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
