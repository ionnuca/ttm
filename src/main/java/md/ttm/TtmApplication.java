package md.ttm;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punctul de intrare al aplicației.
 * <p>
 * Aplicația se poate adăuga pe ecranul telefonului („Add to Home Screen”): iconițele și manifestul
 * sunt fișiere statice proprii (META-INF/resources), nu generate de Vaadin, care nu găsește imaginea
 * în pachetul Spring Boot și ar folosi iconița lui implicită.
 */
@SpringBootApplication
public class TtmApplication implements AppShellConfigurator {

    /** Iconițele (filă, iPhone, Android) și manifestul pentru instalarea pe telefon. */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
        settings.addFavIcon("icon", "icons/favicon-32.png", "32x32");
        settings.addFavIcon("icon", "icons/favicon-16.png", "16x16");
        settings.addFavIcon("apple-touch-icon", "apple-touch-icon.png", "180x180");
        settings.addLink("manifest", "manifest.json");
        settings.addMetaTag("theme-color", "#0B2A63");
        settings.addMetaTag("mobile-web-app-capable", "yes");
        settings.addMetaTag("apple-mobile-web-app-capable", "yes");
        settings.addMetaTag("apple-mobile-web-app-title", "TTM");
    }

    public static void main(String[] args) {
        SpringApplication.run(TtmApplication.class, args);
    }
}
