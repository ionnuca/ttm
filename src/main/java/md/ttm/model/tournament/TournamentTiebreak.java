package md.ttm.model.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Un set de departajare, jucat la un turneu comercial între doi jucători rămași la egalitate
 * după meciurile directe. Se înregistrează scorul la puncte (de exemplu 11:8).
 * Nu este un meci: nu contează la rating și nici la statistici.
 */
@Entity
@Table(name = "tournament_tiebreak")
public class TournamentTiebreak {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    /** Grupa sau finala; {@code null} la turneele Round Robin simple. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id")
    private TournamentGroup group;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_a", nullable = false)
    private TournamentParticipant participantA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_b", nullable = false)
    private TournamentParticipant participantB;

    @Column(name = "points_a", nullable = false)
    private int pointsA;

    @Column(name = "points_b", nullable = false)
    private int pointsB;

    @Column(name = "recorded_by", length = 50)
    private String recordedBy;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    public TournamentTiebreak() {
    }

    public TournamentTiebreak(Tournament tournament, TournamentGroup group, int roundNo,
                              TournamentParticipant a, TournamentParticipant b) {
        this.tournament = tournament;
        this.group = group;
        this.roundNo = roundNo;
        this.participantA = a;
        this.participantB = b;
    }

    /** {@code true} dacă setul e între aceiași doi jucători (în orice ordine). */
    public boolean between(long a, long b) {
        long x = participantA.getId();
        long y = participantB.getId();
        return (x == a && y == b) || (x == b && y == a);
    }

    public Long groupId() {
        return group != null ? group.getId() : null;
    }

    public Long getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public TournamentGroup getGroup() {
        return group;
    }

    public int getRoundNo() {
        return roundNo;
    }

    public TournamentParticipant getParticipantA() {
        return participantA;
    }

    public TournamentParticipant getParticipantB() {
        return participantB;
    }

    public int getPointsA() {
        return pointsA;
    }

    public int getPointsB() {
        return pointsB;
    }

    public void setPoints(int pointsA, int pointsB) {
        this.pointsA = pointsA;
        this.pointsB = pointsB;
    }

    public String getRecordedBy() {
        return recordedBy;
    }

    public void setRecordedBy(String recordedBy) {
        this.recordedBy = recordedBy;
    }

    public Instant getRecordedAt() {
        return recordedAt;
    }

    public void setRecordedAt(Instant recordedAt) {
        this.recordedAt = recordedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TournamentTiebreak other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
