package md.ttm.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.RouterLink;
import md.ttm.i18n.Tr;
import md.ttm.model.notification.Notification;
import md.ttm.service.notification.NotificationService;
import md.ttm.ui.tournament.TournamentView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Clopoțelul din bara de sus: numărul notificărilor necitite și, la apăsare, lista ultimelor notificări
 * (o fereastră, ca să încapă bine și pe telefon). La deschiderea listei notificările devin citite;
 * apăsarea unei notificări deschide turneul și închide lista.
 */
public class NotificationBell extends Div {

    private static final ZoneId ZONE = ZoneId.of("Europe/Chisinau");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final NotificationService notificationService;
    private final Span counter = new Span();
    private final Div list = new Div();
    private final Button clearAll = new Button(VaadinIcon.TRASH.create());
    private final Dialog dialog = new Dialog();

    public NotificationBell(NotificationService notificationService) {
        this.notificationService = notificationService;
        Button bell = new Button(VaadinIcon.BELL.create(), e -> open());
        bell.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        bell.setAriaLabel(Tr.t("Notificări"));
        bell.setTooltipText(Tr.t("Notificări"));

        counter.getStyle()
                .set("position", "absolute").set("top", "0").set("right", "0")
                .set("min-width", "1.1rem").set("height", "1.1rem").set("padding", "0 0.25rem")
                .set("box-sizing", "border-box").set("border-radius", "999px")
                .set("background", "var(--lumo-error-color)").set("color", "var(--lumo-error-contrast-color)")
                .set("font-size", "0.7rem").set("font-weight", "700").set("line-height", "1.1rem")
                .set("text-align", "center").set("pointer-events", "none");
        getStyle().set("position", "relative").set("display", "inline-flex");
        add(bell, counter);
        updateCounter(notificationService.countUnread());

        dialog.setHeaderTitle(Tr.t("Notificări"));
        dialog.setWidth("min(94vw, 26rem)");
        dialog.getElement().getStyle().set("--lumo-space-l", "var(--lumo-space-m)");
        clearAll.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        clearAll.setAriaLabel(Tr.t("Șterge toate"));
        clearAll.setTooltipText(Tr.t("Șterge toate"));
        clearAll.addClickListener(e -> {
            notificationService.deleteAll();
            fill();
        });
        Button close = new Button(VaadinIcon.CLOSE.create(), e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        close.setAriaLabel(Tr.t("Închide"));
        dialog.getHeader().add(clearAll, close);
        list.getStyle().set("width", "100%");
        dialog.add(list);
    }

    private void open() {
        fill();
        dialog.open();
        notificationService.markAllRead();
        updateCounter(0);
    }

    private void updateCounter(long unread) {
        counter.setText(unread > 99 ? "99+" : String.valueOf(unread));
        counter.setVisible(unread > 0);
    }

    private void fill() {
        list.removeAll();
        var notifications = notificationService.findRecent();
        clearAll.setVisible(!notifications.isEmpty());
        if (notifications.isEmpty()) {
            Span empty = new Span(Tr.t("Nu aveți notificări."));
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
            list.add(empty);
            return;
        }
        notifications.forEach(n -> list.add(item(n)));
    }

    private Div item(Notification n) {
        Span text = new Span(text(n));
        text.getStyle().set("font-weight", n.isRead() ? "400" : "600").set("line-height", "1.35");

        Span when = new Span(WHEN.format(n.getCreatedAt().atZone(ZONE)));
        Div meta = new Div(when);
        meta.getStyle()
                .set("display", "flex").set("flex-wrap", "wrap").set("gap", "0 var(--lumo-space-m)")
                .set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-xs)");
        if (n.getTournamentId() != null) {
            RouterLink link = new RouterLink(Tr.t("Deschide turneul"), TournamentView.class, n.getTournamentId());
            link.getElement().addEventListener("click", e -> dialog.close());
            meta.add(link);
        }
        Div body = new Div(text, meta);
        body.getStyle().set("flex", "1").set("min-width", "0").set("font-size", "var(--lumo-font-size-s)");

        Button delete = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> {
            notificationService.delete(n.getId());
            fill();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        delete.setAriaLabel(Tr.t("Șterge notificarea"));
        delete.setTooltipText(Tr.t("Șterge notificarea"));
        delete.getStyle().set("flex", "none").set("margin", "0").set("color", "var(--lumo-secondary-text-color)");

        Div item = new Div(body, delete);
        item.getStyle()
                .set("display", "flex").set("align-items", "flex-start").set("gap", "var(--lumo-space-xs)")
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
        return item;
    }

    /** Textul notificării, în limba interfeței. */
    public static String text(Notification n) {
        return switch (n.getType()) {
            case REGISTRATION_REQUESTED -> Tr.t("{0} a cerut înscrierea la turneul „{1}”", n.getPlayerName(), n.getTournamentName());
            case REGISTRATION_CONFIRMED -> Tr.t("Înscrierea la turneul „{0}” a fost confirmată", n.getTournamentName());
            case REGISTRATION_REJECTED -> Tr.t("Înscrierea la turneul „{0}” a fost respinsă", n.getTournamentName());
            case REGISTRATION_EXPIRED -> Tr.t("Turneul „{0}” a început fără ca înscrierea să fie confirmată", n.getTournamentName());
        };
    }
}
