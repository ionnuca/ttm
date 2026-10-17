package md.ttm.repository;

import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsername(String username);

    boolean existsByUsername(String username);

    Optional<AppUser> findByPlayerId(Long playerId);

    long countByRoleAndEnabledTrue(Role role);

    List<AppUser> findAllByOrderByUsernameAsc();

    /** Conturile active cu unul dintre rolurile date (de exemplu organizatorii, pentru notificări). */
    List<AppUser> findByEnabledTrueAndRoleIn(java.util.Collection<md.ttm.model.user.Role> roles);
}
