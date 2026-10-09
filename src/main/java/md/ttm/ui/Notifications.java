package md.ttm.ui;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;

/**
 * Mesaje scurte afișate utilizatorului.
 */
public final class Notifications {

    private Notifications() {
    }

    public static void success(String message) {
        Notification n = Notification.show(message, 3000, Notification.Position.BOTTOM_START);
        n.addThemeVariants(NotificationVariant.LUMO_SUCCESS);
    }

    public static void error(String message) {
        Notification n = Notification.show(message, 6000, Notification.Position.MIDDLE);
        n.addThemeVariants(NotificationVariant.LUMO_ERROR);
    }
}
