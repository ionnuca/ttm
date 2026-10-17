package md.ttm.service.notification;

import md.ttm.model.notification.Notification;
import md.ttm.model.notification.NotificationType;
import md.ttm.model.player.Player;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.NotificationRepository;
import md.ttm.security.SecurityUtils;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

/**
 * Notificările din aplicație: cererile de înscriere (pentru organizatori) și răspunsul la ele (pentru jucători).
 */
@Service
@Transactional(readOnly = true)
public class NotificationService {

    /** Câte notificări se afișează în listă. */
    public static final int RECENT = 30;

    private final NotificationRepository notificationRepository;
    private final AppUserRepository userRepository;

    public NotificationService(NotificationRepository notificationRepository, AppUserRepository userRepository) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
    }

    /** Ultimele notificări ale utilizatorului curent. */
    @PreAuthorize("isAuthenticated()")
    public List<Notification> findRecent() {
        return currentUser()
                .map(u -> notificationRepository.findRecent(u.getId(), PageRequest.of(0, RECENT)))
                .orElse(List.of());
    }

    /** Numărul notificărilor necitite ale utilizatorului curent (0 pentru vizitatori). */
    public long countUnread() {
        return currentUser().map(u -> notificationRepository.countByUserIdAndReadAtIsNull(u.getId())).orElse(0L);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void markAllRead() {
        currentUser().ifPresent(u -> notificationRepository.markAllRead(u.getId(), Instant.now()));
    }

    /** Notifică jucătorul (dacă are cont). */
    @Transactional
    public void notifyPlayer(Player player, NotificationType type, Tournament tournament) {
        userRepository.findByPlayerId(player.getId())
                .ifPresent(user -> notificationRepository.save(new Notification(user, type, tournament, null)));
    }

    /** Notifică toți organizatorii activi (administratori și manageri de turnee), în afară de cel care a făcut acțiunea. */
    @Transactional
    public void notifyOrganizers(NotificationType type, Tournament tournament, String playerName) {
        String self = SecurityUtils.currentUsername().orElse(null);
        for (AppUser user : userRepository.findByEnabledTrueAndRoleIn(EnumSet.of(Role.ADMIN, Role.TOURNAMENT_MANAGER))) {
            if (!user.getUsername().equals(self)) {
                notificationRepository.save(new Notification(user, type, tournament, playerName));
            }
        }
    }

    private Optional<AppUser> currentUser() {
        return SecurityUtils.currentUsername().flatMap(userRepository::findByUsername);
    }
}
