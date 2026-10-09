package md.ttm.repository;

import md.ttm.model.rating.RatingHistory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface RatingHistoryRepository extends JpaRepository<RatingHistory, Long> {

    /** Istoricul unui jucător, cu turneul încărcat, cel mai recent primul. */
    @Query("""
            select h from RatingHistory h join fetch h.tournament t
            where h.player.id = :playerId
            order by t.tournamentDate desc, t.id desc""")
    List<RatingHistory> findByPlayerIdWithTournament(@Param("playerId") Long playerId);

    @Query("select h from RatingHistory h where h.tournament.id = :tournamentId")
    List<RatingHistory> findByTournamentId(@Param("tournamentId") Long tournamentId);
}
