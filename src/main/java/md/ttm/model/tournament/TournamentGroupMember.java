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

/**
 * Un jucător dintr-o grupă, cu poziția lui în grupă.
 */
@Entity
@Table(name = "tournament_group_member")
public class TournamentGroupMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private TournamentGroup group;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "participant_id", nullable = false)
    private TournamentParticipant participant;

    @Column(name = "seed", nullable = false)
    private int seed;

    public TournamentGroupMember() {
    }

    public TournamentGroupMember(TournamentGroup group, TournamentParticipant participant, int seed) {
        this.group = group;
        this.participant = participant;
        this.seed = seed;
    }

    public Long getId() {
        return id;
    }

    public TournamentGroup getGroup() {
        return group;
    }

    public TournamentParticipant getParticipant() {
        return participant;
    }

    public int getSeed() {
        return seed;
    }
}
