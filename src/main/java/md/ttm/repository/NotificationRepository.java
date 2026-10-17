package md.ttm.repository;

import md.ttm.model.notification.Notification;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** Ultimele notificări ale unui utilizator, cele mai noi primele. */
    @Query("select n from Notification n where n.user.id = :userId order by n.createdAt desc, n.id desc")
    List<Notification> findRecent(@Param("userId") Long userId, Pageable page);

    long countByUserIdAndReadAtIsNull(Long userId);

    /** Notificările unui utilizator despre turneul dat, de tipurile date (se înlocuiesc cu cea nouă). */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Notification n
            where n.user.id = :userId and n.tournament.id = :tournamentId and n.type in :types""")
    int deleteForUserAndTournament(@Param("userId") Long userId, @Param("tournamentId") Long tournamentId,
                                   @Param("types") java.util.Collection<md.ttm.model.notification.NotificationType> types);

    /** Cererile de înscriere ale unui jucător la un turneu, la toți organizatorii (cererea s-a rezolvat). */
    @Modifying(flushAutomatically = true)
    @Query("""
            delete from Notification n
            where n.type = md.ttm.model.notification.NotificationType.REGISTRATION_REQUESTED
              and n.tournament.id = :tournamentId and n.playerName = :playerName""")
    int deleteRequests(@Param("tournamentId") Long tournamentId, @Param("playerName") String playerName);

    @Modifying(flushAutomatically = true)
    @Query("delete from Notification n where n.id = :id and n.user.id = :userId")
    int deleteOwn(@Param("id") Long id, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true)
    @Query("delete from Notification n where n.user.id = :userId")
    int deleteAllOwn(@Param("userId") Long userId);

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}
