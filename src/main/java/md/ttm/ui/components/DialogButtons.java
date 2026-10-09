package md.ttm.ui.components;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.icon.VaadinIcon;

/**
 * Butoane comune în subsolul dialogurilor.
 */
public final class DialogButtons {

    private DialogButtons() {
    }

    /** Butonul „Șterge”, aliniat la stânga; închide dialogul și apoi cere confirmarea. */
    public static Button delete(Dialog dialog, Runnable onDelete) {
        Button delete = new Button("Șterge", VaadinIcon.TRASH.create(), e -> {
            dialog.close();
            onDelete.run();
        });
        delete.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
        delete.getStyle().set("margin-inline-end", "auto");
        return delete;
    }
}
