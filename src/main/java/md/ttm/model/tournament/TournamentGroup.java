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

import java.util.Objects;

/**
 * O grupă dintr-un turneu „Grupe + finale”: Grupa A, B … în etapa 1, Finala 1 și Finala 2 în etapa 2.
 */
@Entity
@Table(name = "tournament_group")
public class TournamentGroup {

    public static final int FINAL_1 = 1;
    public static final int FINAL_2 = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Enumerated(EnumType.STRING)
    @Column(name = "stage", nullable = false, length = 20)
    private TournamentStage stage;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    public TournamentGroup() {
    }

    public TournamentGroup(Tournament tournament, TournamentStage stage, int position, String name) {
        this.tournament = tournament;
        this.stage = stage;
        this.position = position;
        this.name = name;
    }

    /** „Grupa A”, „Grupa B” … */
    public static String groupName(int position) {
        return "Grupa " + (char) ('A' + position - 1);
    }

    public boolean isFinal() {
        return stage == TournamentStage.FINALS;
    }

    public Long getId() {
        return id;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public TournamentStage getStage() {
        return stage;
    }

    public int getPosition() {
        return position;
    }

    public String getName() {
        return name;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof TournamentGroup other)) {
            return false;
        }
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
