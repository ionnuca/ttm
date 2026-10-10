package md.ttm.repository;

import md.ttm.model.tournament.TournamentGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentGroupRepository extends JpaRepository<TournamentGroup, Long> {

    List<TournamentGroup> findByTournamentIdOrderByStageAscPositionAsc(Long tournamentId);
}
