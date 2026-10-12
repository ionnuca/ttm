package md.ttm.service.tournament;

import md.ttm.model.tournament.TournamentGroup;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentTiebreak;

import java.util.List;

/**
 * O grupă (sau finală) cu jucătorii, meciurile și tabelul ei.
 *
 * @param members   jucătorii, în ordinea din grupă
 * @param matches   meciurile care se joacă în această grupă
 * @param carried   doar la finale: meciurile directe din etapa 1, preluate în tabel (nu se mai joacă)
 * @param standings tabelul, în ordinea din grupă
 * @param tiebreaks seturile de departajare jucate în această grupă
 * @param pendingTiebreaks seturile de departajare care mai trebuie jucate
 */
public record GroupView(TournamentGroup group,
                        List<TournamentParticipant> members,
                        List<TournamentMatch> matches,
                        List<TournamentMatch> carried,
                        List<StandingsCalculator.Row> standings,
                        List<TournamentTiebreak> tiebreaks,
                        List<StandingsCalculator.Pairing> pendingTiebreaks) {

    /** Toate meciurile au rezultat și nu mai sunt seturi de departajare de jucat. */
    public boolean complete() {
        return matches.stream().allMatch(TournamentMatch::isPlayed) && pendingTiebreaks.isEmpty();
    }

    public long playedMatches() {
        return matches.stream().filter(TournamentMatch::isPlayed).count();
    }
}
