package md.ttm.ui;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.data.binder.ValidationResult;
import md.ttm.user.AppUser;
import md.ttm.user.Role;
import md.ttm.user.UserForm;
import md.ttm.user.UserService;

import java.util.function.Consumer;

/**
 * Formularul de creare/editare a unui utilizator (administrator).
 * La creare se face și profilul de jucător, cu datele minime: nume și prenume.
 */
public class UserFormDialog extends Dialog {

    private final TextField lastName = new TextField("Nume");
    private final TextField firstName = new TextField("Prenume");
    private final TextField username = new TextField("Nume de utilizator");
    private final PasswordField password = new PasswordField("Parolă");
    private final Select<Role> role = new Select<>();
    private final Checkbox enabled = new Checkbox("Cont activ (se poate autentifica)");

    private final BeanValidationBinder<UserForm> binder = new BeanValidationBinder<>(UserForm.class);

    /**
     * @param user   utilizatorul editat sau {@code null} pentru unul nou
     * @param onSave   apelat cu datele validate; excepțiile aruncate sunt afișate în dialog
     * @param onDelete apelat la apăsarea butonului „Șterge”; {@code null} ascunde butonul
     */
    public UserFormDialog(AppUser user, Consumer<UserForm> onSave, Runnable onDelete) {
        boolean creating = user == null;
        boolean hasPlayer = creating || user.getPlayer() != null;
        UserForm form = creating ? new UserForm() : UserForm.from(user);

        setHeaderTitle(creating ? "Utilizator nou" : "Editare utilizator");
        setWidth("min(95vw, 36rem)");

        role.setLabel("Rol");
        role.setItems(Role.values());
        role.setItemLabelGenerator(Role::getLabel);
        username.setHelperText("Litere latine, cifre și . _ -");
        password.setHelperText(creating
                ? "Cel puțin " + UserService.MIN_PASSWORD_LENGTH + " caractere"
                : "Lăsați gol pentru a păstra parola actuală");

        if (hasPlayer) {
            binder.forField(lastName).asRequired("Introduceți numele").bind("lastName");
            binder.forField(firstName).asRequired("Introduceți prenumele").bind("firstName");
        }
        binder.forField(username).asRequired("Introduceți numele de utilizator").bind("username");
        binder.forField(password)
                .withValidator((value, ctx) -> {
                    if (value == null || value.isEmpty()) {
                        return creating ? ValidationResult.error("Introduceți parola") : ValidationResult.ok();
                    }
                    return value.length() >= UserService.MIN_PASSWORD_LENGTH
                            ? ValidationResult.ok()
                            : ValidationResult.error("Cel puțin " + UserService.MIN_PASSWORD_LENGTH + " caractere");
                })
                .bind("password");
        binder.forField(role).asRequired("Alegeți rolul").bind("role");
        binder.forField(enabled).bind("enabled");
        binder.readBean(form);

        FormLayout layout = new FormLayout();
        if (hasPlayer) {
            layout.add(lastName, firstName);
        }
        layout.add(username, password, role, enabled);
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("26rem", 2));
        layout.setColspan(enabled, 2);
        add(layout);

        Button save = new Button("Salvează", e -> {
            if (binder.writeBeanIfValid(form)) {
                try {
                    onSave.accept(form);
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

        (hasPlayer ? lastName : username).focus();
    }
}
