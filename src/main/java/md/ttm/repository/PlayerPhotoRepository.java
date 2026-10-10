package md.ttm.repository;

import md.ttm.model.player.PlayerPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;

public interface PlayerPhotoRepository extends JpaRepository<PlayerPhoto, Long> {

    /** Jucătorii care au poză și momentul ultimei schimbări (fără conținutul imaginilor). */
    @Query("select p.playerId as playerId, p.updatedAt as updatedAt from PlayerPhoto p")
    List<PhotoStamp> findAllStamps();

    interface PhotoStamp {
        Long getPlayerId();

        Instant getUpdatedAt();
    }
}
