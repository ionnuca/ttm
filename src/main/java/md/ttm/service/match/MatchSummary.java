package md.ttm.service.match;

import md.ttm.model.player.Player;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentStatus;

import java.time.Instant;
import java.time.LocalDate;

/**
 * Un meci cu rezultat, pregătit pentru afișare (lista „Meciuri” și pagina jucătorului).
 *
 * @param stage  grupa sau finala, la turneele „Grupe + finale”; altfel {@code null}
 * @param rated  {@code true} dacă turneul e încheiat (meciul a contat la rating)
 */
public record MatchSummary(Long matchId, Long tournamentId, String tournamentName, LocalDate tournamentDate,
                           Instant recordedAt, String stage, Player playerA, Player playerB,
                           Integer setsA, Integer setsB, boolean walkover, boolean aWon,
                           Integer ratingDeltaA, Integer ratingDeltaB, boolean rated) {

    static MatchSummary of(TournamentMatch m) {
        boolean aWon = m.getWinner().getId().equals(m.getParticipantA().getId());
        return new MatchSummary(m.getId(), m.getTournament().getId(), m.getTournament().getName(),
                m.getTournament().getTournamentDate(), m.getRecordedAt(),
                m.getGroup() != null ? m.getGroup().getName() : null,
                m.getParticipantA().getPlayer(), m.getParticipantB().getPlayer(),
                m.getSetsA(), m.getSetsB(), m.isWalkover(), aWon,
                m.getRatingDeltaA(), m.getRatingDeltaB(),
                m.getTournament().getStatus() == TournamentStatus.FINISHED);
    }

    public Player winner() {
        return aWon ? playerA : playerB;
    }

    /** Meciul văzut de un jucător: adversarul, scorul lui și schimbarea lui de rating. */
    public Perspective from(Long playerId) {
        boolean isA = playerA.getId().equals(playerId);
        return new Perspective(isA ? playerB : playerA, isA == aWon,
                isA ? setsA : setsB, isA ? setsB : setsA, isA ? ratingDeltaA : ratingDeltaB);
    }

    public record Perspective(Player opponent, boolean won, Integer ownSets, Integer opponentSets,
                              Integer ratingDelta) {
    }
}
