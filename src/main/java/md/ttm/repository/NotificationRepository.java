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

    @Modifying
    @Query("update Notification n set n.readAt = :at where n.user.id = :userId and n.readAt is null")
    int markAllRead(@Param("userId") Long userId, @Param("at") Instant at);
}
