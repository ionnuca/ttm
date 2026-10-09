package md.ttm.repository;

import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, Long> {

    /** Participanții unui turneu, cu jucătorul încărcat. */
    @Query("select p from TournamentParticipant p join fetch p.player where p.tournament.id = :tournamentId")
    List<TournamentParticipant> findByTournamentIdWithPlayer(@Param("tournamentId") Long tournamentId);

    Optional<TournamentParticipant> findByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    boolean existsByTournamentIdAndPlayerId(Long tournamentId, Long playerId);

    long countByTournamentId(Long tournamentId);

    /** Numărul de participanți pentru fiecare turneu: [tournamentId, count]. */
    @Query("select p.tournament.id, count(p) from TournamentParticipant p group by p.tournament.id")
    List<Object[]> countByTournament();

    boolean existsByPlayerIdAndTournamentStatusNot(Long playerId, TournamentStatus status);

    List<TournamentParticipant> findByPlayerId(Long playerId);
}
