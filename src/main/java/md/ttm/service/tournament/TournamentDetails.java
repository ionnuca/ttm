package md.ttm.service.tournament;

import md.ttm.model.rating.RatingHistory;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Tot ce afișează pagina unui turneu, plus ce are voie să facă utilizatorul curent.
 *
 * @param participants participanții: după începere în ordinea din grupă, înainte după rating
 * @param standings    tabelul (gol înainte de începerea turneului), în ordinea din grupă
 * @param prizePool    suma acumulată (doar la turneele comerciale)
 * @param ownParticipant participarea utilizatorului curent, dacă e înscris
 * @param ratingChanges  ratingul înainte/după turneu, pe jucător (doar la turneele încheiate)
 * @param groups         la „Grupe + finale”: grupele etapei 1, apoi finalele (gol la Round Robin)
 */
public record TournamentDetails(
        Tournament tournament,
        List<TournamentParticipant> participants,
        List<TournamentMatch> matches,
        List<StandingsCalculator.Row> standings,
        BigDecimal prizePool,
        List<PrizePlace> prizes,
        TournamentParticipant ownParticipant,
        boolean manager,
        boolean canSelfRegister,
        boolean canRecordResults,
        Map<Long, RatingHistory> ratingChanges,
        List<GroupView> groups) {

    public List<GroupView> groupStage() {
        return groups.stream().filter(g -> !g.group().isFinal()).toList();
    }

    public List<GroupView> finals() {
        return groups.stream().filter(g -> g.group().isFinal()).toList();
    }

    /** Etapa 1 s-a terminat și se poate porni etapa 2. */
    /** Organizatorul poate încheia manual un turneu în desfășurare. */
    public boolean canFinishManually() {
        return manager && tournament.getStatus() == TournamentStatus.IN_PROGRESS
                && !TournamentService.requiresFinalsBeforeFinish(tournament);
    }

    /** Meciurile încă fără rezultat (după o încheiere manuală: cele care nu s-au mai jucat). */
    public long unplayedMatches() {
        return matches.size() - playedMatches();
    }

    public boolean canStartFinals() {
        return manager && tournament.isGroupsFormat()
                && tournament.getStage() == md.ttm.model.tournament.TournamentStage.GROUPS
                && !groupStage().isEmpty() && groupStage().stream().allMatch(GroupView::complete);
    }

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
