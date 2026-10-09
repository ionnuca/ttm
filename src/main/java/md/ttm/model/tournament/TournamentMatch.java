package md.ttm.model.tournament;

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
import jakarta.persistence.Version;

import java.time.Instant;
import java.util.Objects;

/**
 * Un meci dintr-un turneu, între doi participanți. Rezultatul e gol până la înregistrare.
 */
@Entity
@Table(name = "tournament_match")
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "round_no", nullable = false)
    private int roundNo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_a", nullable = false)
    private TournamentParticipant participantA;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_b", nullable = false)
    private TournamentParticipant participantB;

    @Column(name = "sets_a")
    private Integer setsA;

    @Column(name = "sets_b")
    private Integer setsB;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", length = 20)
    private MatchOutcome outcome;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "winner_id")
    private TournamentParticipant winner;

    @Column(name = "recorded_by", length = 50)
    private String recordedBy;

    @Column(name = "recorded_at")
    private Instant recordedAt;

    public TournamentMatch() {
    }

    public TournamentMatch(Tournament tournament, int roundNo, TournamentParticipant a, TournamentParticipant b) {
        this.tournament = tournament;
        this.roundNo = roundNo;
        this.participantA = a;
        this.participantB = b;
    }

    public boolean isPlayed() {
        return outcome != null;
    }

    public boolean isWalkover() {
        return outcome == MatchOutcome.WALKOVER;
    }

    /** Șterge rezultatul (meciul redevine nejucat). */
    public void clearResult() {
        setsA = null;
        setsB = null;
        outcome = null;
        winner = null;
        recordedBy = null;
        recordedAt = null;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public Tournament getTournament() {
        return tournament;
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

    public Integer getSetsA() {
        return setsA;
    }

    public void setSetsA(Integer setsA) {
        this.setsA = setsA;
    }

    public Integer getSetsB() {
        return setsB;
    }

    public void setSetsB(Integer setsB) {
        this.setsB = setsB;
    }

    public MatchOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(MatchOutcome outcome) {
        this.outcome = outcome;
    }

    public TournamentParticipant getWinner() {
        return winner;
    }

    public void setWinner(TournamentParticipant winner) {
        this.winner = winner;
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
        if (!(o instanceof TournamentMatch other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
