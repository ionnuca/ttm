package md.ttm.ui.admin;

import md.ttm.i18n.Tr;
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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.streams.DownloadHandler;
import jakarta.annotation.security.RolesAllowed;
import md.ttm.service.admin.BackupService;
import md.ttm.service.admin.TestEnvironmentService;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import com.vaadin.flow.component.AttachEvent;
import com.vaadin.flow.component.DetachEvent;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import java.time.format.DateTimeFormatter;
import md.ttm.ui.layout.MainLayout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.ZoneId;
import java.time.ZonedDateTime;

/** Setări și acțiuni ale administratorului: backup-ul bazei de date și pornirea/oprirea aplicației de test. */
@Route(value = "setari", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class SettingsView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Setări") + " | TTM";
    }


    private final TestEnvironmentService testEnvironment;
    private final Div testCard = new Div();

    public SettingsView(BackupService backupService, ObjectProvider<BuildProperties> buildProperties,
                        TestEnvironmentService testEnvironment) {
        this.testEnvironment = testEnvironment;
        setMaxWidth("44rem");
        H2 title = new H2(Tr.t("Setări"));
        title.getStyle().set("margin-bottom", "0");
        BuildProperties build = buildProperties.getIfAvailable();
        Span version = new Span(Tr.t("Versiunea aplicației: {0}", build != null ? build.getVersion() : Tr.t("necunoscută")));
        version.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(title, version, createBackupCard(backupService));
        if (testEnvironment.available()) {
            styleCard(testCard);
            add(testCard);
            renderTestCard();
        }
    }

    // ------------------------------------------------------------------
    // Aplicația de test
    // ------------------------------------------------------------------

    private void renderTestCard() {
        testCard.removeAll();
        TestEnvironmentService.Status status = testEnvironment.status();
        boolean desired = testEnvironment.desiredOn();
        boolean installed = testEnvironment.serverServiceInstalled();

        Span badge = switch (status.state()) {
            case RUNNING -> Badges.badge(Tr.t("Pornită"), Badges.Tone.SUCCESS);
            case STARTING -> Badges.badge(Tr.t("Se pornește…"), Badges.Tone.PRIMARY);
            case STOPPED -> Badges.badge(desired ? Tr.t("Se pornește…") : Tr.t("Oprită"),
                    desired ? Badges.Tone.PRIMARY : Badges.Tone.CONTRAST);
            case UNKNOWN -> Badges.badge(Tr.t("Stare necunoscută"), Badges.Tone.CONTRAST);
        };
        if (status.state() == TestEnvironmentService.State.RUNNING && !desired) {
            badge = Badges.badge(Tr.t("Se oprește…"), Badges.Tone.PRIMARY);
        }
        H3 heading = new H3(Tr.t("Aplicația de test"));
        heading.getStyle().set("margin", "0");
        HorizontalLayout head = new HorizontalLayout(heading, badge);
        head.setAlignItems(FlexComponent.Alignment.CENTER);

        Paragraph text = new Paragraph(Tr.t("Versiunea din develop, cu o copie a datelor din producție. Implicit e oprită, ca să nu ocupe memoria serverului (până la 640 MB); porniți-o doar când aveți nevoie. Pornirea durează aproximativ un minut."));
        Button start = new Button(Tr.t("Pornește"), VaadinIcon.PLAY.create(), e -> change(true));
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        Button stop = new Button(Tr.t("Oprește"), VaadinIcon.STOP.create(), e -> change(false));
        stop.addThemeVariants(ButtonVariant.LUMO_ERROR);
        start.setEnabled(!desired);
        stop.setEnabled(desired);
        Button reload = new Button(Tr.t("Reîmprospătează"), VaadinIcon.REFRESH.create(), e -> renderTestCard());
        reload.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
        HorizontalLayout actions = new HorizontalLayout(start, stop, reload);
        actions.getStyle().set("flex-wrap", "wrap");
        testCard.add(head, text, actions);

        if (status.state() == TestEnvironmentService.State.RUNNING && desired && !testEnvironment.url().isBlank()) {
            Anchor open = new Anchor(testEnvironment.url(), Tr.t("Deschide aplicația de test →"));
            open.setTarget("_blank");
            testCard.add(open);
        }
        Span note = new Span(installed
                ? (status.checkedAt() != null
                    ? Tr.t("Verificat de server la {0}.", DateTimeFormatter.ofPattern("HH:mm:ss")
                        .format(status.checkedAt().atZone(ZoneId.of("Europe/Chisinau"))))
                    : "")
                : Tr.t("Serviciul de pe server nu a răspuns încă. Dacă nu l-ați instalat, rulați o dată pe server: sudo bash test-control-setup.sh"));
        note.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
        testCard.add(note);

        // cât timp starea cerută nu e încă cea reală, pagina se actualizează singură
        boolean pending = (desired && status.state() != TestEnvironmentService.State.RUNNING)
                || (!desired && status.state() != TestEnvironmentService.State.STOPPED);
        getUI().ifPresent(ui -> ui.setPollInterval(pending && installed ? 5000 : -1));
    }

    private void change(boolean on) {
        try {
            testEnvironment.setDesired(on);
            Notifications.success(on ? Tr.t("Aplicația de test se pornește") : Tr.t("Aplicația de test se oprește"));
        } catch (RuntimeException ex) {
            Notifications.error(Notifications.saveError(ex));
        }
        renderTestCard();
    }

    @Override
    protected void onAttach(AttachEvent attachEvent) {
        super.onAttach(attachEvent);
        if (testEnvironment.available()) {
            attachEvent.getUI().addPollListener(e -> renderTestCard());
            renderTestCard();
        }
    }

    @Override
    protected void onDetach(DetachEvent detachEvent) {
        detachEvent.getUI().setPollInterval(-1);
        super.onDetach(detachEvent);
    }

    private static void styleCard(Div card) {
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-s)")
                .set("padding", "var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("width", "100%")
                .set("box-sizing", "border-box");
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
        Button button = new Button(Tr.t("Descarcă backup-ul"), VaadinIcon.DOWNLOAD.create());
        button.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Anchor download = new Anchor(handler, "");
        download.getElement().setAttribute("download", true);
        download.add(button);

        Paragraph text = new Paragraph(Tr.t("Descarcă toate datele aplicației, așa cum sunt acum: jucătorii, conturile, turneele, rezultatele, istoricul ratingului și pozele. Fișierul SQL se poate restaura pe server cu psql și înlocuiește atunci toate datele existente."));
        Span restore = new Span(Tr.t("Restaurare pe server:") + " docker compose exec -T db psql -U ttm -d ttm < " + Tr.t("<fișier>") + ".sql");
        restore.getStyle()
                .set("font-family", "var(--lumo-font-family-monospace, monospace)")
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("overflow-wrap", "anywhere");
        Span note = new Span(Tr.t("Serverul face oricum un backup automat în fiecare noapte (03:30), păstrat 14 zile. Fișierul descărcat conține parolele criptate ale utilizatorilor: păstrați-l în siguranță."));
        note.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");

        Div card = new Div(new H3(Tr.t("Backup bază de date")), text, download, restore, note);
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
