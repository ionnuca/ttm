package md.ttm;

import com.vaadin.flow.component.page.AppShellConfigurator;
import com.vaadin.flow.server.AppShellSettings;
import com.vaadin.flow.server.PWA;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Punctul de intrare al aplicației.
 * <p>
 * {@link PWA} permite instalarea aplicației pe telefon ("Add to Home Screen").
 */
@SpringBootApplication
@PWA(name = "TTM - Tenis de masă", shortName = "TTM",
        description = "Jucători, clasament și turnee de tenis de masă",
        iconPath = "icons/icon.png")
public class TtmApplication implements AppShellConfigurator {

    /** Iconița din fila browserului: SVG pentru browserele moderne, PNG ca rezervă, iar pentru iPhone apple-touch-icon. */
    @Override
    public void configurePage(AppShellSettings settings) {
        settings.addFavIcon("icon", "icons/favicon.svg", "any");
        settings.addFavIcon("icon", "icons/favicon-32.png", "32x32");
        settings.addFavIcon("icon", "icons/favicon-16.png", "16x16");
        settings.addFavIcon("apple-touch-icon", "apple-touch-icon.png", "180x180");
    }

    public static void main(String[] args) {
        SpringApplication.run(TtmApplication.class, args);
    }
}
