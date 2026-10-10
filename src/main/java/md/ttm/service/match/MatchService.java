package md.ttm.service.match;

import md.ttm.model.player.Player;
import md.ttm.model.rating.RatingHistory;
import md.ttm.repository.TournamentMatchRepository;
import md.ttm.repository.TournamentParticipantRepository;
import md.ttm.service.player.PlayerService;
import md.ttm.service.player.RankedPlayer;
import md.ttm.service.rating.RatingService;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/** Meciurile jucate și statistica jucătorilor. Totul e public (vizibil și vizitatorilor). */
@Service
@Transactional(readOnly = true)
public class MatchService {

    private final TournamentMatchRepository matchRepository;
    private final TournamentParticipantRepository participantRepository;
    private final PlayerService playerService;
    private final RatingService ratingService;

    public MatchService(TournamentMatchRepository matchRepository,
                        TournamentParticipantRepository participantRepository,
                        PlayerService playerService, RatingService ratingService) {
        this.matchRepository = matchRepository;
        this.participantRepository = participantRepository;
        this.playerService = playerService;
        this.ratingService = ratingService;
    }

    /** Ultimele meciuri cu rezultat, din toate turneele, cele mai noi primele. */
    public List<MatchSummary> findRecent(int limit) {
        return matchRepository.findRecentPlayed(PageRequest.of(0, limit)).stream()
                .map(MatchSummary::of)
                .toList();
    }

    public Optional<PlayerProfile> findPlayerProfile(Long playerId) {
        List<RankedPlayer> ranked = playerService.findRanked();
        return ranked.stream()
                .filter(rp -> rp.player().getId().equals(playerId))
                .findFirst()
                .map(rp -> profile(rp, ranked.size()));
    }

    private PlayerProfile profile(RankedPlayer rankedPlayer, int rankedPlayers) {
        Player player = rankedPlayer.player();
        List<MatchSummary> matches = matchRepository.findPlayedByPlayerId(player.getId()).stream()
                .map(MatchSummary::of)
                .toList();
        List<RatingHistory> history = ratingService.findHistory(player.getId());
        int tournaments = (int) participantRepository.findByPlayerId(player.getId()).stream()
                .filter(p -> p.getTournament().isStarted())
                .count();
        return new PlayerProfile(player, rankedPlayer.rank(), rankedPlayers,
                stats(player, matches, history, tournaments), matches, history);
    }

    static PlayerStats stats(Player player, List<MatchSummary> matches, List<RatingHistory> history,
                             int tournaments) {
        int wins = 0, losses = 0, walkoverWins = 0, walkoverLosses = 0, setsWon = 0, setsLost = 0;
        for (MatchSummary match : matches) {
            MatchSummary.Perspective p = match.from(player.getId());
            if (match.walkover()) {
                if (p.won()) {
                    walkoverWins++;
                } else {
                    walkoverLosses++;
                }
                continue;
            }
            if (p.won()) {
                wins++;
            } else {
                losses++;
            }
            setsWon += p.ownSets();
            setsLost += p.opponentSets();
        }
        int best = Math.max(player.getInitialRating(), player.getRating());
        for (RatingHistory h : history) {
            best = Math.max(best, h.getRatingAfter());
        }
        return new PlayerStats(wins, losses, walkoverWins, walkoverLosses, setsWon, setsLost, tournaments, best);
    }
}
