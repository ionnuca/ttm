package md.ttm.config;

import org.springframework.boot.web.server.MimeMappings;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.boot.web.servlet.server.ConfigurableServletWebServerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Setări ale serverului web. */
@Configuration
public class WebConfig {

    /** Manifestul aplicației (instalarea pe telefon) trebuie servit ca application/manifest+json. */
    @Bean
    public WebServerFactoryCustomizer<ConfigurableServletWebServerFactory> mimeMappings() {
        return factory -> {
            MimeMappings mappings = new MimeMappings(MimeMappings.DEFAULT);
            mappings.add("webmanifest", "application/manifest+json");
            factory.setMimeMappings(mappings);
        };
    }
}
