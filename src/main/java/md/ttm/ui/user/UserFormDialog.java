package md.ttm.ui.user;

import md.ttm.i18n.Tr;
import md.ttm.model.user.AppUser;
import md.ttm.model.user.Role;
import md.ttm.service.user.UserForm;
import md.ttm.service.user.UserService;
import md.ttm.ui.components.DialogButtons;
import md.ttm.ui.components.Notifications;
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

import java.util.function.Consumer;

/**
 * Formularul de creare/editare a unui utilizator (administrator).
 * La creare se face și profilul de jucător, cu datele minime: nume și prenume.
 */
public class UserFormDialog extends Dialog {

    private final TextField lastName = new TextField(Tr.t("Nume"));
    private final TextField firstName = new TextField(Tr.t("Prenume"));
    private final TextField username = new TextField(Tr.t("Nume de utilizator"));
    private final PasswordField password = new PasswordField(Tr.t("Parolă"));
    private final Select<Role> role = new Select<>();
    private final Checkbox enabled = new Checkbox(Tr.t("Cont activ (se poate autentifica)"));
    private final Checkbox trusted = new Checkbox(Tr.t("Se înscrie la turnee fără confirmare"));

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

        setHeaderTitle(creating ? Tr.t("Utilizator nou") : Tr.t("Editare utilizator"));
        setWidth("min(95vw, 36rem)");

        role.setLabel(Tr.t("Rol"));
        role.setItems(Role.values());
        role.setItemLabelGenerator(Role::getLabel);
        username.setHelperText(Tr.t("Litere latine, cifre și . _ -"));
        password.setHelperText(creating
                ? Tr.t("Cel puțin {0} caractere", UserService.MIN_PASSWORD_LENGTH)
                : Tr.t("Lăsați gol pentru a păstra parola actuală"));

        if (hasPlayer) {
            binder.forField(lastName).asRequired(Tr.t("Introduceți numele")).bind("lastName");
            binder.forField(firstName).asRequired(Tr.t("Introduceți prenumele")).bind("firstName");
        }
        binder.forField(username).asRequired(Tr.t("Introduceți numele de utilizator")).bind("username");
        binder.forField(password)
                .withValidator((value, ctx) -> {
                    if (value == null || value.isEmpty()) {
                        return creating ? ValidationResult.error(Tr.t("Introduceți parola")) : ValidationResult.ok();
                    }
                    return value.length() >= UserService.MIN_PASSWORD_LENGTH
                            ? ValidationResult.ok()
                            : ValidationResult.error(Tr.t("Cel puțin {0} caractere", UserService.MIN_PASSWORD_LENGTH));
                })
                .bind("password");
        binder.forField(role).asRequired(Tr.t("Alegeți rolul")).bind("role");
        binder.forField(enabled).bind("enabled");
        binder.forField(trusted).bind("trusted");
        trusted.setTooltipText(Tr.t("Cererea de înscriere la turneu e confirmată automat; organizatorii se înscriu oricum direct"));
        binder.readBean(form);

        FormLayout layout = new FormLayout();
        if (hasPlayer) {
            layout.add(lastName, firstName);
        }
        layout.add(username, password, role, enabled, trusted);
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("26rem", 2));
        layout.setColspan(enabled, 2);
        layout.setColspan(trusted, 2);
        add(layout);

        Button save = new Button(Tr.t("Salvează"), e -> {
            if (binder.writeBeanIfValid(form)) {
                try {
                    onSave.accept(form);
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

        (hasPlayer ? lastName : username).focus();
    }
}
