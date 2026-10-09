package md.ttm.model.rating;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;

/**
 * Ratingul unui jucător înainte și după un turneu încheiat.
 */
@Entity
@Table(name = "rating_history")
public class RatingHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "player_id", nullable = false)
    private Player player;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tournament_id", nullable = false)
    private Tournament tournament;

    @Column(name = "rating_before", nullable = false)
    private int ratingBefore;

    @Column(name = "rating_after", nullable = false)
    private int ratingAfter;

    @Column(name = "k_factor", nullable = false)
    private int kFactor;

    @Column(name = "rated_matches", nullable = false)
    private int ratedMatches;

    public RatingHistory() {
    }

    public RatingHistory(Player player, Tournament tournament, int ratingBefore, int ratingAfter,
                         int kFactor, int ratedMatches) {
        this.player = player;
        this.tournament = tournament;
        this.ratingBefore = ratingBefore;
        this.ratingAfter = ratingAfter;
        this.kFactor = kFactor;
        this.ratedMatches = ratedMatches;
    }

    public int getChange() {
        return ratingAfter - ratingBefore;
    }

    public Long getId() {
        return id;
    }

    public Player getPlayer() {
        return player;
    }

    public Tournament getTournament() {
        return tournament;
    }

    public int getRatingBefore() {
        return ratingBefore;
    }

    public int getRatingAfter() {
        return ratingAfter;
    }

    public int getKFactor() {
        return kFactor;
    }

    public int getRatedMatches() {
        return ratedMatches;
    }
}
