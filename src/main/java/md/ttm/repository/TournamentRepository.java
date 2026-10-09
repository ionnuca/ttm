package md.ttm.repository;

import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findAllByOrderByTournamentDateDescIdDesc();

    /** Turneele într-o anumită stare, în ordine cronologică (data, apoi ordinea creării). */
    List<Tournament> findByStatusOrderByTournamentDateAscIdAsc(TournamentStatus status);

    /**
     * Șterge turneul direct în baza de date; participanții, meciurile și istoricul de rating
     * se șterg în cascadă (ON DELETE CASCADE). Contextul JPA se golește, ca să nu rămână
     * entități care trimit la turneul șters.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from Tournament t where t.id = :id")
    void deleteWithChildren(@Param("id") Long id);
}
