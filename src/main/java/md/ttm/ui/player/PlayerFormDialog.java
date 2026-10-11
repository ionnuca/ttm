package md.ttm.ui.player;

import md.ttm.i18n.Tr;
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

    private final TextField lastName = new TextField(Tr.t("Nume"));
    private final TextField firstName = new TextField(Tr.t("Prenume"));
    private final RadioButtonGroup<PlayStyle> playStyle = new RadioButtonGroup<>(Tr.t("Stil de joc"));
    private final TextField city = new TextField(Tr.t("Oraș"));
    private final TextField phone = new TextField(Tr.t("Telefon"));
    private final IntegerField initialRating = new IntegerField(Tr.t("Rating inițial"));

    private final BeanValidationBinder<Player> binder = new BeanValidationBinder<>(Player.class);

    /**
     * @param onDelete apelat la apăsarea butonului „Șterge”; {@code null} ascunde butonul
     */
    public PlayerFormDialog(Player player, Consumer<Player> onSave, Runnable onDelete) {
        setHeaderTitle(player.getId() == null ? Tr.t("Jucător nou") : Tr.t("Editare jucător"));
        setWidth("min(95vw, 40rem)");

        playStyle.setItems(PlayStyle.values());
        playStyle.setItemLabelGenerator(PlayStyle::getLabel);
        phone.setPlaceholder("+373 ...");
        initialRating.setMin(0);
        initialRating.setMax(5000);
        initialRating.setStepButtonsVisible(true);
        initialRating.setStep(10);
        initialRating.setHelperText(Tr.t("Punctul de plecare (implicit {0}). Ratingul curent = ratingul inițial + schimbările din turneele încheiate.", Player.DEFAULT_RATING));

        binder.forField(lastName).asRequired(Tr.t("Introduceți numele")).bind("lastName");
        binder.forField(firstName).asRequired(Tr.t("Introduceți prenumele")).bind("firstName");
        binder.forField(playStyle).asRequired(Tr.t("Alegeți stilul de joc")).bind("playStyle");
        binder.forField(city).bind("city");
        binder.forField(phone).bind("phone");
        binder.forField(initialRating).asRequired(Tr.t("Introduceți ratingul inițial")).bind("initialRating");
        PlayerDetailsFields details = new PlayerDetailsFields(binder);
        binder.readBean(player);

        FormLayout form = new FormLayout(lastName, firstName, playStyle);
        details.addPlayHandTo(form);
        form.add(city, phone);
        details.addEquipmentTo(form);
        H4 statsTitle = new H4(Tr.t("Rating"));
        statsTitle.getStyle().set("margin", "var(--lumo-space-m) 0 0");
        form.add(statsTitle, initialRating);
        form.setColspan(statsTitle, 2);
        if (player.getId() != null) {
            Span current = new Span(Tr.t("Rating curent: {0} · victorii / înfrângeri: {1} / {2} (calculate din turnee)", player.getRating(), player.getWins(), player.getLosses()));
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

        Button save = new Button(Tr.t("Salvează"), e -> {
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
        Button cancel = new Button(Tr.t("Anulează"), e -> close());
        if (onDelete != null) {
            getFooter().add(DialogButtons.delete(this, onDelete));
        }
        getFooter().add(cancel, save);

        lastName.focus();
    }
}
