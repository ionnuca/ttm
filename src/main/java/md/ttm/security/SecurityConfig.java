package md.ttm.security;

import md.ttm.ui.account.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Securitatea aplicației.
 * <ul>
 *     <li>Accesul la pagini se decide prin adnotările de pe view-uri:
 *     {@code @AnonymousAllowed} (Guest), {@code @PermitAll} (orice utilizator logat),
 *     {@code @RolesAllowed("ADMIN")} (doar administratorul).</li>
 *     <li>Operațiile de scriere sunt protejate suplimentar în servicii cu {@code @PreAuthorize}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig extends VaadinWebSecurity {

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        super.configure(http);
        setLoginView(http, LoginView.class);
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
