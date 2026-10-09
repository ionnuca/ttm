package md.ttm;

import com.vaadin.flow.component.page.AppShellConfigurator;
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
        description = "Jucători, clasament și turnee de tenis de masă")
public class TtmApplication implements AppShellConfigurator {

    public static void main(String[] args) {
        SpringApplication.run(TtmApplication.class, args);
    }
}
