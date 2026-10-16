package md.ttm.security;

import md.ttm.ui.account.LoginView;
import com.vaadin.flow.spring.security.VaadinWebSecurity;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

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

    /** Numele cookie-ului „ține-mă minte”. */
    public static final String REMEMBER_ME_COOKIE = "ttm-remember";

    private final AppUserDetailsService userDetailsService;
    private final String rememberMeKey;
    private final int rememberMeDays;

    public SecurityConfig(AppUserDetailsService userDetailsService,
                          @Value("${app.remember-me.key:}") String rememberMeKey,
                          @Value("${spring.datasource.password:}") String databasePassword,
                          @Value("${app.remember-me.days:30}") int rememberMeDays) {
        this.userDetailsService = userDetailsService;
        this.rememberMeKey = rememberMeKey.isBlank() ? derivedKey(databasePassword) : rememberMeKey;
        this.rememberMeDays = rememberMeDays;
    }

    @Override
    protected void configure(HttpSecurity http) throws Exception {
        // Autentificarea se păstrează pe dispozitiv (implicit 30 de zile), și după închiderea browserului
        // sau a aplicației de pe telefon și după repornirea serverului; „Ieșire” o șterge.
        http.rememberMe(rememberMe -> rememberMe
                .key(rememberMeKey)
                .userDetailsService(userDetailsService)
                .alwaysRemember(true)
                .rememberMeCookieName(REMEMBER_ME_COOKIE)
                .tokenValiditySeconds(rememberMeDays * 24 * 60 * 60));
        // Iconițele cerute automat de browsere și telefoane (Safari pe iPhone: apple-touch-icon*.png)
        // și pozele jucătorilor (publice, ca și numele lor)
        http.authorizeHttpRequests(auth -> auth.requestMatchers(
                "/favicon.ico", "/apple-touch-icon.png", "/apple-touch-icon-precomposed.png", "/icons/**",
                "/manifest.json",
                "/foto/**")
                .permitAll());
        super.configure(http);
        setLoginView(http, LoginView.class);
    }

    /**
     * Cheia cu care se semnează cookie-ul „ține-mă minte”. Trebuie să fie secretă și aceeași la fiecare
     * pornire; dacă nu e setată (APP_REMEMBER_ME_KEY), se derivă din parola bazei de date.
     */
    private static String derivedKey(String secret) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(("ttm-remember-me:" + secret).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
