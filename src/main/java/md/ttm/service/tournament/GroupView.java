package md.ttm.service.tournament;

import md.ttm.model.tournament.TournamentGroup;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;

import java.util.List;

/**
 * O grupă (sau finală) cu jucătorii, meciurile și tabelul ei.
 *
 * @param members   jucătorii, în ordinea din grupă
 * @param matches   meciurile care se joacă în această grupă
 * @param carried   doar la finale: meciurile directe din etapa 1, preluate în tabel (nu se mai joacă)
 * @param standings tabelul, în ordinea din grupă
 */
public record GroupView(TournamentGroup group,
                        List<TournamentParticipant> members,
                        List<TournamentMatch> matches,
                        List<TournamentMatch> carried,
                        List<StandingsCalculator.Row> standings) {

    public boolean complete() {
        return matches.stream().allMatch(TournamentMatch::isPlayed);
    }

    public long playedMatches() {
        return matches.stream().filter(TournamentMatch::isPlayed).count();
    }
}
