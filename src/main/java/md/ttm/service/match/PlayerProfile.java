package md.ttm.service.match;

import md.ttm.model.player.Player;
import md.ttm.model.rating.RatingHistory;

import java.util.List;

/** Tot ce arată pagina unui jucător. */
public record PlayerProfile(Player player, int rank, int rankedPlayers, PlayerStats stats,
                            List<MatchSummary> matches, List<RatingHistory> ratingHistory) {
}
