package md.ttm.service.tournament;

import md.ttm.model.tournament.MatchOutcome;

/**
 * Rezultatul introdus pentru un meci.
 *
 * @param outcome         NORMAL (pe seturi) sau WALKOVER (victorie tehnică)
 * @param setsA           seturile primului jucător (doar la NORMAL)
 * @param setsB           seturile celui de-al doilea jucător (doar la NORMAL)
 * @param walkoverWinner  participantul care câștigă tehnic (doar la WALKOVER)
 */
public record MatchResultForm(MatchOutcome outcome, Integer setsA, Integer setsB, Long walkoverWinner) {

    public static MatchResultForm sets(int setsA, int setsB) {
        return new MatchResultForm(MatchOutcome.NORMAL, setsA, setsB, null);
    }

    public static MatchResultForm walkover(long winnerParticipantId) {
        return new MatchResultForm(MatchOutcome.WALKOVER, null, null, winnerParticipantId);
    }
}
