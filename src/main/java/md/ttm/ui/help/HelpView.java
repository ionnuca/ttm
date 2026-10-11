package md.ttm.ui.help;

import md.ttm.i18n.Language;
import md.ttm.i18n.Tr;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.ui.layout.MainLayout;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Ghidul de utilizare, public: cum se folosește aplicația, cum se organizează un turneu,
 * cum se calculează clasamentul în grupă și ratingul.
 * Textul e în {@code resources/help/<limbă>.html}, câte un fișier pentru fiecare limbă.
 */
@Route(value = "ajutor", layout = MainLayout.class)
@AnonymousAllowed
public class HelpView extends VerticalLayout implements HasDynamicTitle {

    private static final String STYLE = """
            <style>
              .ttm-help h2 { margin: 0 0 var(--lumo-space-s); }
              .ttm-help h3 { margin: var(--lumo-space-xl) 0 var(--lumo-space-s); }
              .ttm-help table { border-collapse: collapse; width: 100%; margin: var(--lumo-space-s) 0; font-size: var(--lumo-font-size-s); }
              .ttm-help th, .ttm-help td { border: 1px solid var(--lumo-contrast-20pct); padding: var(--lumo-space-xs) var(--lumo-space-s); text-align: left; vertical-align: top; }
              .ttm-help th { background: var(--lumo-contrast-5pct); }
              .ttm-help .formula { font-family: var(--lumo-font-family-monospace, monospace); background: var(--lumo-contrast-5pct);
                                   border-radius: var(--lumo-border-radius-m); padding: var(--lumo-space-s) var(--lumo-space-m); overflow-x: auto; white-space: pre; }
              .ttm-help .note { color: var(--lumo-secondary-text-color); font-size: var(--lumo-font-size-s); }
            </style>
            """;

    public HelpView() {
        setMaxWidth("48rem");
        add(new Html("<div class=\"ttm-help\" style=\"line-height:1.6;max-width:100%\">"
                + STYLE + content(Tr.current()) + "</div>"));
    }

    @Override
    public String getPageTitle() {
        return Tr.t("Ghid de utilizare") + " | TTM";
    }

    /** Ghidul în limba cerută; dacă lipsește, cel în română. */
    static String content(Language language) {
        String text = read("/help/" + language.code() + ".html");
        return text != null ? text : read("/help/ro.html");
    }

    private static String read(String path) {
        try (InputStream in = HelpView.class.getResourceAsStream(path)) {
            return in == null ? null : new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
