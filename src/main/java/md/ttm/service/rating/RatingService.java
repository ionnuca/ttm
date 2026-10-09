package md.ttm.service.rating;

import md.ttm.model.player.Player;
import md.ttm.model.rating.RatingHistory;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.repository.PlayerRepository;
import md.ttm.repository.RatingHistoryRepository;
import md.ttm.repository.TournamentMatchRepository;
import md.ttm.repository.TournamentParticipantRepository;
import md.ttm.repository.TournamentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ratingul Elo al jucătorilor.
 * <p>
 * Ratingul nu se modifică incremental, ci se <b>reconstruiește</b>: se pornește de la ratingul
 * inițial al fiecărui jucător și se aplică, în ordine cronologică, toate turneele încheiate.
 * Astfel, corectarea unui rezultat (sau ștergerea unui turneu) se reflectă corect și în
 * turneele de după. Pentru un club, calculul durează câteva milisecunde.
 * <p>
 * Tot aici se calculează victoriile și înfrângerile jucătorilor (inclusiv cele tehnice).
 */
@Service
@Transactional
public class RatingService {

    private static final Logger log = LoggerFactory.getLogger(RatingService.class);

    private final PlayerRepository playerRepository;
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentMatchRepository matchRepository;
    private final RatingHistoryRepository historyRepository;

    public RatingService(PlayerRepository playerRepository,
                         TournamentRepository tournamentRepository,
                         TournamentParticipantRepository participantRepository,
                         TournamentMatchRepository matchRepository,
                         RatingHistoryRepository historyRepository) {
        this.playerRepository = playerRepository;
        this.tournamentRepository = tournamentRepository;
        this.participantRepository = participantRepository;
        this.matchRepository = matchRepository;
        this.historyRepository = historyRepository;
    }

    /** Istoricul ratingului unui jucător, cel mai recent turneu primul. */
    @Transactional(readOnly = true)
    public List<RatingHistory> findHistory(Long playerId) {
        return historyRepository.findByPlayerIdWithTournament(playerId);
    }

    /** Recalculează ratingul, victoriile și înfrângerile tuturor jucătorilor. */
    public void recalculateAll() {
        List<Player> players = playerRepository.findAll();
        Map<Long, PlayerState> state = new HashMap<>();
        for (Player player : players) {
            state.put(player.getId(), new PlayerState(player.getInitialRating()));
        }

        historyRepository.deleteAllInBatch();
        List<RatingHistory> history = new ArrayList<>();

        for (Tournament tournament : tournamentRepository.findByStatusOrderByTournamentDateAscIdAsc(TournamentStatus.FINISHED)) {
            List<TournamentParticipant> participants = participantRepository.findByTournamentIdWithPlayer(tournament.getId());
            List<TournamentMatch> matches = matchRepository.findByTournamentIdWithParticipants(tournament.getId());

            List<TournamentRatingCalculator.Entrant> entrants = new ArrayList<>();
            for (TournamentParticipant p : participants) {
                PlayerState s = state.get(p.getPlayer().getId());
                entrants.add(new TournamentRatingCalculator.Entrant(p.getPlayer().getId(), s.rating, s.ratedMatches));
            }
            List<TournamentRatingCalculator.Game> games = new ArrayList<>();
            for (TournamentMatch m : matches) {
                if (!m.isPlayed()) {
                    continue;
                }
                long a = m.getParticipantA().getPlayer().getId();
                long b = m.getParticipantB().getPlayer().getId();
                boolean aWon = m.getWinner().getId().equals(m.getParticipantA().getId());
                state.get(aWon ? a : b).wins++;
                state.get(aWon ? b : a).losses++;
                if (!m.isWalkover()) {
                    games.add(new TournamentRatingCalculator.Game(m.getId(), a, b, aWon));
                }
            }

            TournamentRatingCalculator.Result result = TournamentRatingCalculator.calculate(entrants, games);
            for (TournamentMatch m : matches) {
                TournamentRatingCalculator.GameDelta delta = result.gameDeltas().get(m.getId());
                m.setRatingDeltas(delta != null ? delta.deltaA() : null, delta != null ? delta.deltaB() : null);
            }
            for (TournamentParticipant p : participants) {
                long playerId = p.getPlayer().getId();
                PlayerState s = state.get(playerId);
                int after = result.ratingAfter().get(playerId);
                history.add(new RatingHistory(p.getPlayer(), tournament, s.rating, after,
                        result.kFactor().get(playerId), result.ratedMatches().get(playerId)));
                s.rating = after;
                s.ratedMatches += result.ratedMatches().get(playerId);
            }
        }

        // turneele redeschise (un rezultat șters) nu mai au schimbări de rating
        for (Tournament tournament : tournamentRepository.findByStatusOrderByTournamentDateAscIdAsc(TournamentStatus.IN_PROGRESS)) {
            for (TournamentMatch m : matchRepository.findByTournamentIdWithParticipants(tournament.getId())) {
                if (m.getRatingDeltaA() != null || m.getRatingDeltaB() != null) {
                    m.setRatingDeltas(null, null);
                }
            }
        }

        historyRepository.saveAll(history);
        for (Player player : players) {
            PlayerState s = state.get(player.getId());
            if (player.getRating() != s.rating || player.getWins() != s.wins || player.getLosses() != s.losses) {
                player.setRating(s.rating);
                player.setWins(s.wins);
                player.setLosses(s.losses);
            }
        }
        log.debug("Rating recalculat pentru {} jucători", players.size());
    }

    private static final class PlayerState {
        private int rating;
        private int ratedMatches;
        private int wins;
        private int losses;

        private PlayerState(int rating) {
            this.rating = rating;
        }
    }
}
