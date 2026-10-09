package md.ttm.security;

import md.ttm.user.Role;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

/**
 * Acces la utilizatorul autentificat în cererea curentă.
 */
public final class SecurityUtils {

    private SecurityUtils() {
    }

    /** Numele utilizatorului autentificat; gol pentru vizitatori (Guest). */
    public static Optional<String> currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        return Optional.ofNullable(auth.getName());
    }

    public static boolean isAuthenticated() {
        return currentUsername().isPresent();
    }

    public static boolean hasRole(Role role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            return false;
        }
        String authority = "ROLE_" + role.name();
        return auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(authority::equals);
    }

    public static boolean isAdmin() {
        return hasRole(Role.ADMIN);
    }
}
