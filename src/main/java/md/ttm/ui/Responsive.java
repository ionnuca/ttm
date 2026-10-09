package md.ttm.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.page.Page;
import com.vaadin.flow.shared.Registration;

import java.util.function.Consumer;

/**
 * Adaptarea interfeței la ecrane înguste (telefon), fără CSS separat:
 * componenta primește {@code true} când fereastra e mai îngustă de {@link #NARROW_WIDTH}.
 */
public final class Responsive {

    public static final int NARROW_WIDTH = 700;

    private Responsive() {
    }

    public static void onNarrowChange(Component component, Consumer<Boolean> listener) {
        component.addAttachListener(attach -> {
            Page page = attach.getUI().getPage();
            page.retrieveExtendedClientDetails(details ->
                    listener.accept(details.getWindowInnerWidth() < NARROW_WIDTH));
            Registration resize = page.addBrowserWindowResizeListener(e ->
                    listener.accept(e.getWidth() < NARROW_WIDTH));
            component.addDetachListener(detach -> {
                resize.remove();
                detach.unregisterListener();
            });
        });
    }
}
