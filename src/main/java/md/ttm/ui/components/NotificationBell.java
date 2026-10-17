package md.ttm.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.popover.Popover;
import com.vaadin.flow.component.popover.PopoverPosition;
import com.vaadin.flow.router.RouterLink;
import md.ttm.i18n.Tr;
import md.ttm.model.notification.Notification;
import md.ttm.service.notification.NotificationService;
import md.ttm.ui.tournament.TournamentView;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Clopoțelul din bara de sus: numărul notificărilor necitite și, la apăsare, lista ultimelor notificări.
 * La deschiderea listei notificările devin citite.
 */
public class NotificationBell extends Div {

    private static final ZoneId ZONE = ZoneId.of("Europe/Chisinau");
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final NotificationService notificationService;
    private final Span counter = new Span();
    private final VerticalLayout list = new VerticalLayout();

    public NotificationBell(NotificationService notificationService) {
        this.notificationService = notificationService;
        Button bell = new Button(VaadinIcon.BELL.create());
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

        list.setPadding(false);
        list.setSpacing(false);
        list.setWidth("min(90vw, 22rem)");
        list.getStyle().set("max-height", "60vh").set("overflow-y", "auto");
        Popover popover = new Popover();
        popover.setTarget(bell);
        popover.setPosition(PopoverPosition.BOTTOM_END);
        popover.setModal(false);
        popover.add(list);
        popover.addOpenedChangeListener(e -> {
            if (e.isOpened()) {
                fill();
                notificationService.markAllRead();
                updateCounter(0);
            }
        });
    }

    private void updateCounter(long unread) {
        counter.setText(unread > 99 ? "99+" : String.valueOf(unread));
        counter.setVisible(unread > 0);
    }

    private void fill() {
        list.removeAll();
        Span title = new Span(Tr.t("Notificări"));
        title.getStyle().set("font-weight", "600").set("padding", "var(--lumo-space-xs) 0");
        list.add(title);
        var notifications = notificationService.findRecent();
        if (notifications.isEmpty()) {
            Span empty = new Span(Tr.t("Nu aveți notificări."));
            empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
            list.add(empty);
            return;
        }
        notifications.forEach(n -> list.add(item(n)));
    }

    private static Div item(Notification n) {
        Div item = new Div();
        item.getStyle()
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("width", "100%")
                .set("font-size", "var(--lumo-font-size-s)");
        if (!n.isRead()) {
            item.getStyle().set("font-weight", "600");
        }
        Span text = new Span(text(n));
        Span when = new Span(WHEN.format(n.getCreatedAt().atZone(ZONE)));
        when.getStyle().set("display", "block").set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-xs)").set("font-weight", "400");
        item.add(text);
        if (n.getTournamentId() != null) {
            RouterLink link = new RouterLink(Tr.t("Deschide turneul"), TournamentView.class, n.getTournamentId());
            link.getStyle().set("display", "block").set("font-weight", "400");
            item.add(link);
        }
        item.add(when);
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
