package md.ttm.repository;

import md.ttm.model.tournament.TournamentGroupMember;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentGroupMemberRepository extends JpaRepository<TournamentGroupMember, Long> {

    /** Membrii tuturor grupelor unui turneu, cu grupa, participantul și jucătorul încărcate. */
    @Query("""
            select m from TournamentGroupMember m
              join fetch m.group g
              join fetch m.participant p join fetch p.player
            where g.tournament.id = :tournamentId
            order by g.stage, g.position, m.seed""")
    List<TournamentGroupMember> findByTournamentIdWithParticipants(@Param("tournamentId") Long tournamentId);
}
