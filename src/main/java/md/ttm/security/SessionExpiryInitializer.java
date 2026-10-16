package md.ttm.security;

import com.vaadin.flow.server.CustomizedSystemMessages;
import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinServiceInitListener;
import org.springframework.stereotype.Component;

/**
 * Când sesiunea de pe server a expirat (sau serverul a fost repornit), pagina se reîncarcă singură,
 * fără mesajul „Session Expired”; utilizatorul rămâne autentificat prin cookie-ul „ține-mă minte”.
 */
@Component
public class SessionExpiryInitializer implements VaadinServiceInitListener {

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().setSystemMessagesProvider(info -> {
            CustomizedSystemMessages messages = new CustomizedSystemMessages();
            messages.setSessionExpiredNotificationEnabled(false);
            return messages;
        });
    }
}
