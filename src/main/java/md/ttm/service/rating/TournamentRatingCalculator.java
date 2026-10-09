package md.ttm.service.rating;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Calculează schimbările de rating dintr-un turneu încheiat. Toate meciurile se calculează
 * pe baza ratingurilor de dinaintea turneului, deci ordinea meciurilor nu contează.
 * Victoriile tehnice nu intră în calcul.
 */
public final class TournamentRatingCalculator {

    /** Un jucător la începutul turneului. */
    public record Entrant(long playerId, int rating, int ratedMatchesBefore) {
    }

    /** Un meci jucat efectiv (fără victoriile tehnice). */
    public record Game(long matchId, long playerA, long playerB, boolean aWon) {
    }

    /** Schimbarea afișată pentru fiecare jucător într-un meci (rotunjită individual). */
    public record GameDelta(int deltaA, int deltaB) {
    }

    /**
     * @param ratingAfter     ratingul după turneu, pentru fiecare jucător
     * @param kFactor         factorul K folosit, pentru fiecare jucător
     * @param ratedMatches    meciurile cu rating jucate în turneu, pentru fiecare jucător
     * @param gameDeltas      schimbarea din fiecare meci (pentru afișare)
     */
    public record Result(Map<Long, Integer> ratingAfter, Map<Long, Integer> kFactor,
                         Map<Long, Integer> ratedMatches, Map<Long, GameDelta> gameDeltas) {
    }

    private TournamentRatingCalculator() {
    }

    public static Result calculate(List<Entrant> entrants, List<Game> games) {
        Map<Long, Entrant> byId = new HashMap<>();
        Map<Long, Integer> k = new HashMap<>();
        Map<Long, Double> total = new HashMap<>();
        Map<Long, Integer> played = new HashMap<>();
        for (Entrant e : entrants) {
            byId.put(e.playerId(), e);
            k.put(e.playerId(), Elo.kFactor(e.ratedMatchesBefore()));
            total.put(e.playerId(), 0.0);
            played.put(e.playerId(), 0);
        }

        Map<Long, GameDelta> gameDeltas = new HashMap<>();
        for (Game g : games) {
            Entrant a = byId.get(g.playerA());
            Entrant b = byId.get(g.playerB());
            if (a == null || b == null) {
                continue;
            }
            double deltaA = Elo.change(a.rating(), b.rating(), g.aWon(), k.get(a.playerId()));
            double deltaB = Elo.change(b.rating(), a.rating(), !g.aWon(), k.get(b.playerId()));
            total.merge(a.playerId(), deltaA, Double::sum);
            total.merge(b.playerId(), deltaB, Double::sum);
            played.merge(a.playerId(), 1, Integer::sum);
            played.merge(b.playerId(), 1, Integer::sum);
            gameDeltas.put(g.matchId(), new GameDelta(Elo.round(deltaA), Elo.round(deltaB)));
        }

        Map<Long, Integer> after = new HashMap<>();
        for (Entrant e : entrants) {
            after.put(e.playerId(), Math.max(0, e.rating() + Elo.round(total.get(e.playerId()))));
        }
        return new Result(after, k, played, gameDeltas);
    }
}
