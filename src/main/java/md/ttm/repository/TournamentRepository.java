package md.ttm.repository;

import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findAllByOrderByTournamentDateDescIdDesc();

    /** Turneele într-o anumită stare, în ordine cronologică (data, apoi ordinea creării). */
    List<Tournament> findByStatusOrderByTournamentDateAscIdAsc(TournamentStatus status);
}
