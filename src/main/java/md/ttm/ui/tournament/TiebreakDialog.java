package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.IntegerField;
import md.ttm.i18n.Tr;
import md.ttm.ui.components.Notifications;

import java.util.function.BiConsumer;

/**
 * Scorul unui set de departajare, la puncte (de exemplu 11:8).
 */
class TiebreakDialog extends Dialog {

    /**
     * @param onSave primește punctele jucătorului A și ale jucătorului B; excepțiile se afișează în dialog
     */
    TiebreakDialog(String nameA, String nameB, int round, Integer pointsA, Integer pointsB,
                   BiConsumer<Integer, Integer> onSave) {
        setHeaderTitle(Tr.t("Set de departajare"));
        setWidth("min(95vw, 28rem)");

        Span subtitle = new Span(Tr.t("Runda {0} · un singur set, la 11 puncte, cu 2 puncte diferență", round));
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        IntegerField a = points(nameA, pointsA);
        IntegerField b = points(nameB, pointsB);
        Span colon = new Span(":");
        colon.getStyle().set("font-weight", "700").set("padding-bottom", "var(--lumo-space-s)");
        HorizontalLayout score = new HorizontalLayout(a, colon, b);
        score.setAlignItems(FlexComponent.Alignment.END);
        score.setWidthFull();

        VerticalLayout content = new VerticalLayout(subtitle, score);
        content.setPadding(false);
        add(content);

        Button save = new Button(Tr.t("Salvează"), e -> {
            if (a.getValue() == null || b.getValue() == null) {
                Notifications.error(Tr.t("Introduceți punctele ambilor jucători"));
                return;
            }
            try {
                onSave.accept(a.getValue(), b.getValue());
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button(Tr.t("Anulează"), e -> close());
        getFooter().add(cancel, save);
        a.focus();
    }

    private static IntegerField points(String name, Integer value) {
        IntegerField field = new IntegerField(name);
        field.setMin(0);
        field.setMax(99);
        field.setStepButtonsVisible(true);
        field.setValue(value);
        field.setWidth("50%");
        return field;
    }
}
