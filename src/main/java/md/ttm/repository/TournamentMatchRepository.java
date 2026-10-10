package md.ttm.repository;

import md.ttm.model.tournament.TournamentMatch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    /** Meciurile unui turneu, cu participanții și jucătorii încărcați, în ordinea tururilor. */
    @Query("""
            select m from TournamentMatch m
              join fetch m.participantA a join fetch a.player
              join fetch m.participantB b join fetch b.player
              left join fetch m.winner
              left join fetch m.group
            where m.tournament.id = :tournamentId
            order by m.roundNo, m.id""")
    List<TournamentMatch> findByTournamentIdWithParticipants(@Param("tournamentId") Long tournamentId);

    @Query("""
            select m from TournamentMatch m
              join fetch m.tournament
              left join fetch m.group
              join fetch m.participantA a join fetch a.player
              join fetch m.participantB b join fetch b.player
            where m.id = :id""")
    Optional<TournamentMatch> findByIdWithParticipants(@Param("id") Long id);

    long countByTournamentIdAndOutcomeIsNull(Long tournamentId);

    long countByGroupIdAndOutcomeIsNull(Long groupId);
}
