package md.ttm.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import md.ttm.player.PlayStyle;
import md.ttm.player.Player;

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
    private final IntegerField rating = new IntegerField("Rating");
    private final IntegerField wins = new IntegerField("Victorii");
    private final IntegerField losses = new IntegerField("Înfrângeri");

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
        rating.setMin(0);
        rating.setMax(5000);
        rating.setStepButtonsVisible(true);
        rating.setStep(10);
        rating.setHelperText("Inițial " + Player.DEFAULT_RATING);
        wins.setMin(0);
        losses.setMin(0);

        binder.forField(lastName).asRequired("Introduceți numele").bind("lastName");
        binder.forField(firstName).asRequired("Introduceți prenumele").bind("firstName");
        binder.forField(playStyle).asRequired("Alegeți stilul de joc").bind("playStyle");
        binder.forField(city).bind("city");
        binder.forField(phone).bind("phone");
        binder.forField(rating).asRequired("Introduceți ratingul").bind("rating");
        binder.forField(wins).asRequired("Introduceți numărul de victorii").bind("wins");
        binder.forField(losses).asRequired("Introduceți numărul de înfrângeri").bind("losses");
        binder.readBean(player);

        FormLayout form = new FormLayout(lastName, firstName, playStyle, city, phone, rating, wins, losses);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(playStyle, 2);
        add(form);

        Button save = new Button("Salvează", e -> {
            if (binder.writeBeanIfValid(player)) {
                try {
                    onSave.accept(player);
                    close();
                } catch (RuntimeException ex) {
                    Notifications.error(PlayersView.saveErrorMessage(ex));
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
