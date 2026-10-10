package md.ttm.ui.admin;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import jakarta.annotation.security.RolesAllowed;
import md.ttm.service.admin.BackupService;
import md.ttm.ui.layout.MainLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Setări și acțiuni ale administratorului. Deocamdată: backup-ul bazei de date. */
@Route(value = "setari", layout = MainLayout.class)
@PageTitle("Setări | TTM")
@RolesAllowed("ADMIN")
public class SettingsView extends VerticalLayout {

    public SettingsView(BackupService backupService, ObjectProvider<BuildProperties> buildProperties) {
        setMaxWidth("44rem");
        H2 title = new H2("Setări");
        title.getStyle().set("margin-bottom", "0");
        BuildProperties build = buildProperties.getIfAvailable();
        Span version = new Span("Versiunea aplicației: " + (build != null ? build.getVersion() : "necunoscută"));
        version.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(title, version, createBackupCard(backupService));
    }

    private static Component createBackupCard(BackupService backupService) {
        DownloadHandler handler = event -> {
            byte[] backup = backupService.createBackup();
            event.setFileName(BackupService.fileName(ZonedDateTime.now(ZoneId.of("Europe/Chisinau"))));
            event.setContentType("application/sql");
            try (OutputStream out = event.getOutputStream()) {
                out.write(backup);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        };
        Button button = new Button("Descarcă backup-ul", VaadinIcon.DOWNLOAD.create());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor download = new Anchor(handler, "");
        download.getElement().setAttribute("download", true);
        download.add(button);

        Paragraph text = new Paragraph("Descarcă toate datele aplicației, așa cum sunt acum: jucătorii, conturile, "
                + "turneele, rezultatele, istoricul ratingului și pozele. Fișierul SQL se poate restaura pe server "
                + "cu psql și înlocuiește atunci toate datele existente.");
        Span restore = new Span("Restaurare pe server: docker compose exec -T db psql -U ttm -d ttm < <fișier>.sql");
        restore.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("overflow-wrap", "anywhere");
        Span note = new Span("Serverul face oricum un backup automat în fiecare noapte (03:30), păstrat 14 zile. "
                + "Fișierul descărcat conține parolele criptate ale utilizatorilor: păstrați-l în siguranță.");
        note.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

        Div card = new Div(new H3("Backup bază de date"), text, download, restore, note);
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("padding", "var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("width", "100%")
                .set("box-sizing", "border-box");
        return card;
    }
}
