package md.ttm.model.tournament;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Un turneu: datele de bază, configurarea și starea lui.
 */
@Entity
@Table(name = "tournament")
public class Tournament {

    public static final int DEFAULT_BEST_OF = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    private Long version;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "tournament_date", nullable = false)
    private LocalDate tournamentDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "format", nullable = false, length = 20)
    private TournamentFormat format = TournamentFormat.ROUND_ROBIN;

    @Column(name = "best_of", nullable = false)
    private int bestOf = DEFAULT_BEST_OF;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private TournamentStatus status = TournamentStatus.REGISTRATION;

    @Column(name = "commercial", nullable = false)
    private boolean commercial;

    @Column(name = "winners_count")
    private Integer winnersCount;

    @Column(name = "entry_fee", precision = 10, scale = 2)
    private BigDecimal entryFee;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Seturile necesare pentru a câștiga un meci: 3 la „best of 5”. */
    public int getSetsToWin() {
        return bestOf / 2 + 1;
    }

    public boolean isRegistrationOpen() {
        return status == TournamentStatus.REGISTRATION;
    }

    public boolean isStarted() {
        return status != TournamentStatus.REGISTRATION;
    }

    /** Distribuția premiilor; {@code null} dacă turneul nu e comercial. */
    public PrizeDistribution getPrizeDistribution() {
        return commercial && winnersCount != null ? PrizeDistribution.forWinners(winnersCount) : null;
    }

    public Long getId() {
        return id;
    }

    public Long getVersion() {
        return version;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getTournamentDate() {
        return tournamentDate;
    }

    public void setTournamentDate(LocalDate tournamentDate) {
        this.tournamentDate = tournamentDate;
    }

    public TournamentFormat getFormat() {
        return format;
    }

    public void setFormat(TournamentFormat format) {
        this.format = format;
    }

    public int getBestOf() {
        return bestOf;
    }

    public void setBestOf(int bestOf) {
        this.bestOf = bestOf;
    }

    public TournamentStatus getStatus() {
        return status;
    }

    public void setStatus(TournamentStatus status) {
        this.status = status;
    }

    public boolean isCommercial() {
        return commercial;
    }

    public void setCommercial(boolean commercial) {
        this.commercial = commercial;
    }

    public Integer getWinnersCount() {
        return winnersCount;
    }

    public void setWinnersCount(Integer winnersCount) {
        this.winnersCount = winnersCount;
    }

    public BigDecimal getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(BigDecimal entryFee) {
        this.entryFee = entryFee;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(Instant finishedAt) {
        this.finishedAt = finishedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Tournament other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }

    @Override
    public String toString() {
        return "Tournament[" + id + ", " + name + ", " + status + "]";
    }
}
