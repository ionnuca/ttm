package md.ttm.service.tournament;

import md.ttm.model.tournament.TournamentGroup;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;

import java.util.List;
import java.util.Map;

/**
 * Tabloul eliminatoriu al unui turneu „Grupe + eliminare”.
 *
 * @param members jucătorii calificați, în ordinea de calificare (capii de serie)
 * @param matches meciurile create (cele care se joacă), după locul din tablou
 * @param state   tabloul calculat: toate meciurile, inclusiv cele încă necunoscute, și locurile
 */
public record KnockoutView(TournamentGroup group,
                           List<TournamentParticipant> members,
                           Map<String, TournamentMatch> matches,
                           Bracket.State state) {

    public TournamentParticipant participant(Long id) {
        if (id == null) {
            return null;
        }
        return members.stream().filter(p -> p.getId().equals(id)).findFirst().orElse(null);
    }
}
