package md.ttm.ui.components;

import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.notification.NotificationVariant;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

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

    /** Mesajul afișat când salvarea eșuează; tratează separat editarea simultană. */
    public static String saveError(RuntimeException ex) {
        if (ex instanceof ObjectOptimisticLockingFailureException) {
            return "Datele au fost modificate între timp de altcineva. Reîncărcați pagina și încercați din nou.";
        }
        return ex.getMessage();
    }
}
