package md.ttm.ui.player;

import md.ttm.i18n.Tr;
import md.ttm.security.SecurityUtils;
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

import java.util.function.BiConsumer;
import java.util.function.Consumer;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.textfield.PasswordField;

/**
 * Formularul de adăugare/editare a unui jucător. La adăugare, administratorul poate crea și contul de utilizator.
 */
public class PlayerFormDialog extends Dialog {

    private final TextField lastName = new TextField(Tr.t("Nume"));
    private final TextField firstName = new TextField(Tr.t("Prenume"));
    private final RadioButtonGroup<PlayStyle> playStyle = new RadioButtonGroup<>(Tr.t("Stil de joc"));
    private final TextField city = new TextField(Tr.t("Oraș"));
    private final TextField phone = new TextField(Tr.t("Telefon"));
    private final IntegerField initialRating = new IntegerField(Tr.t("Rating inițial"));

    private final BeanValidationBinder<Player> binder = new BeanValidationBinder<>(Player.class);
    private final Checkbox createAccount = new Checkbox(Tr.t("Creează și cont de utilizator"));
    private final TextField username = new TextField(Tr.t("Nume de utilizator"));
    private final PasswordField password = new PasswordField(Tr.t("Parolă"));

    /** Contul cerut la adăugarea jucătorului; {@code null} dacă bifa nu e pusă. */
    public record Account(String username, String password) {
    }

    /**
     * @param onDelete apelat la apăsarea butonului „Șterge”; {@code null} ascunde butonul
     */
    public PlayerFormDialog(Player player, Consumer<Player> onSave, Runnable onDelete) {
        this(player, (p, account) -> onSave.accept(p), onDelete);
    }

    /**
     * @param onSave apelat cu jucătorul și, la adăugare cu bifa „Creează și cont de utilizator”, cu contul cerut
     */
    public PlayerFormDialog(Player player, BiConsumer<Player, Account> onSave, Runnable onDelete) {
        setHeaderTitle(player.getId() == null ? Tr.t("Jucător nou") : Tr.t("Editare jucător"));
        setWidth("min(95vw, 40rem)");

        playStyle.setItems(PlayStyle.values());
        playStyle.setItemLabelGenerator(PlayStyle::getLabel);
        phone.setPlaceholder("+373 ...");
        // telefoanele le vede doar administratorul
        phone.setVisible(SecurityUtils.isAdmin());
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
        // contul de utilizator: doar administratorul, doar la adăugare
        boolean accountOption = player.getId() == null && SecurityUtils.isAdmin();
        if (accountOption) {
            H4 accountTitle = new H4(Tr.t("Cont de utilizator"));
            accountTitle.getStyle().set("margin", "var(--lumo-space-m) 0 0");
            createAccount.setHelperText(Tr.t("Jucătorul se va putea autentifica (rol Utilizator); fără bifă se creează doar jucătorul"));
            username.setHelperText(Tr.t("Litere latine, cifre și . _ -"));
            password.setHelperText(Tr.t("Cel puțin {0} caractere", md.ttm.service.user.UserService.MIN_PASSWORD_LENGTH));
            username.setVisible(false);
            password.setVisible(false);
            createAccount.addValueChangeListener(e -> {
                username.setVisible(e.getValue());
                password.setVisible(e.getValue());
            });
            form.add(accountTitle, createAccount, username, password);
            form.setColspan(accountTitle, 2);
            form.setColspan(createAccount, 2);
        }
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        add(form);

        Button save = new Button(Tr.t("Salvează"), e -> {
            Account account = null;
            if (accountOption && createAccount.getValue()) {
                boolean ok = true;
                if (username.getValue().isBlank()) {
                    username.setInvalid(true);
                    username.setErrorMessage(Tr.t("Introduceți numele de utilizator"));
                    ok = false;
                }
                if (password.getValue().length() < md.ttm.service.user.UserService.MIN_PASSWORD_LENGTH) {
                    password.setInvalid(true);
                    password.setErrorMessage(Tr.t("Cel puțin {0} caractere", md.ttm.service.user.UserService.MIN_PASSWORD_LENGTH));
                    ok = false;
                }
                if (!ok) {
                    return;
                }
                account = new Account(username.getValue().trim(), password.getValue());
            }
            if (binder.writeBeanIfValid(player)) {
                try {
                    onSave.accept(player, account);
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
