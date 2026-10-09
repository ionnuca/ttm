package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;

import java.math.BigDecimal;
import java.util.List;

/**
 * Tot ce afișează pagina unui turneu, plus ce are voie să facă utilizatorul curent.
 *
 * @param participants participanții: după începere în ordinea din grupă, înainte după rating
 * @param standings    tabelul (gol înainte de începerea turneului), în ordinea din grupă
 * @param prizePool    suma acumulată (doar la turneele comerciale)
 * @param ownParticipant participarea utilizatorului curent, dacă e înscris
 */
public record TournamentDetails(
        Tournament tournament,
        List<TournamentParticipant> participants,
        List<TournamentMatch> matches,
        List<StandingsCalculator.Row> standings,
        BigDecimal prizePool,
        List<PrizePlace> prizes,
        TournamentParticipant ownParticipant,
        boolean admin,
        boolean canSelfRegister,
        boolean canRecordResults) {

    public long playedMatches() {
        return matches.stream().filter(TournamentMatch::isPlayed).count();
    }

    public boolean canSelfUnregister() {
        return tournament.isRegistrationOpen() && ownParticipant != null;
    }

    public TournamentParticipant participant(long id) {
        return participants.stream().filter(p -> p.getId() == id).findFirst().orElseThrow();
    }
}
