package md.ttm.ui.player;

import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.ui.components.DialogButtons;
import md.ttm.ui.components.Notifications;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;

import java.util.function.Consumer;

/**
 * Formularul de adăugare/editare a unui jucător (administrator).
 */
public class PlayerFormDialog extends Dialog {

    private final TextField lastName = new TextField("Nume");
    private final TextField firstName = new TextField("Prenume");
    private final RadioButtonGroup<PlayStyle> playStyle = new RadioButtonGroup<>("Stil de joc");
    private final TextField city = new TextField("Oraș");
    private final TextField phone = new TextField("Telefon");
    private final IntegerField initialRating = new IntegerField("Rating inițial");

    private final BeanValidationBinder<Player> binder = new BeanValidationBinder<>(Player.class);

    /**
     * @param onDelete apelat la apăsarea butonului „Șterge”; {@code null} ascunde butonul
     */
    public PlayerFormDialog(Player player, Consumer<Player> onSave, Runnable onDelete) {
        setHeaderTitle(player.getId() == null ? "Jucător nou" : "Editare jucător");
        setWidth("min(95vw, 40rem)");

        playStyle.setItems(PlayStyle.values());
        playStyle.setItemLabelGenerator(PlayStyle::getLabel);
        phone.setPlaceholder("+373 ...");
        initialRating.setMin(0);
        initialRating.setMax(5000);
        initialRating.setStepButtonsVisible(true);
        initialRating.setStep(10);
        initialRating.setHelperText("Punctul de plecare (implicit " + Player.DEFAULT_RATING
                + "). Ratingul curent = ratingul inițial + schimbările din turneele încheiate.");

        binder.forField(lastName).asRequired("Introduceți numele").bind("lastName");
        binder.forField(firstName).asRequired("Introduceți prenumele").bind("firstName");
        binder.forField(playStyle).asRequired("Alegeți stilul de joc").bind("playStyle");
        binder.forField(city).bind("city");
        binder.forField(phone).bind("phone");
        binder.forField(initialRating).asRequired("Introduceți ratingul inițial").bind("initialRating");
        PlayerDetailsFields details = new PlayerDetailsFields(binder);
        binder.readBean(player);

        FormLayout form = new FormLayout(lastName, firstName, playStyle);
        details.addPlayHandTo(form);
        form.add(city, phone);
        details.addEquipmentTo(form);
        H4 statsTitle = new H4("Rating");
        statsTitle.getStyle().set("margin", "var(--lumo-space-m) 0 0");
        form.add(statsTitle, initialRating);
        form.setColspan(statsTitle, 2);
        if (player.getId() != null) {
            Span current = new Span("Rating curent: " + player.getRating() + " · victorii / înfrângeri: "
                    + player.getWins() + " / " + player.getLosses() + " (calculate din turnee)");
            current.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)")
                    .set("align-self", "center");
            form.add(current);
        }
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        add(form);

        Button save = new Button("Salvează", e -> {
            if (binder.writeBeanIfValid(player)) {
                try {
                    onSave.accept(player);
                    close();
                } catch (RuntimeException ex) {
                    Notifications.error(Notifications.saveError(ex));
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        Button cancel = new Button("Anulează", e -> close());
        if (onDelete != null) {
            getFooter().add(DialogButtons.delete(this, onDelete));
        }
        getFooter().add(cancel, save);

        lastName.focus();
    }
}
