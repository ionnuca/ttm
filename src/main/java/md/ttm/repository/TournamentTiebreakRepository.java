package md.ttm.repository;

import md.ttm.model.tournament.TournamentTiebreak;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentTiebreakRepository extends JpaRepository<TournamentTiebreak, Long> {

    /** Seturile de departajare ale unui turneu, cu jucătorii încărcați. */
    @Query("""
            select t from TournamentTiebreak t
              left join fetch t.group
              join fetch t.participantA a join fetch a.player
              join fetch t.participantB b join fetch b.player
            where t.tournament.id = :tournamentId
            order by t.roundNo, t.id""")
    List<TournamentTiebreak> findByTournamentIdWithParticipants(@Param("tournamentId") Long tournamentId);
}
