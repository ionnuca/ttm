package md.ttm.ui.account;

import md.ttm.i18n.Tr;
import md.ttm.common.BusinessException;
import md.ttm.security.SecurityUtils;
import md.ttm.service.user.RegistrationForm;
import md.ttm.service.user.UserService;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.PlayersView;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Crearea simplă a unui cont, cu informații minime. Contul nou este de tip Utilizator
 * și are automat un profil de jucător cu ratingul inițial.
 */
@Route(value = "inregistrare", layout = MainLayout.class)
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Cont nou") + " | TTM";
    }


    private final UserService userService;

    public RegisterView(UserService userService) {
        this.userService = userService;
        setAlignItems(FlexComponent.Alignment.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.setMaxWidth("28rem");
        card.setPadding(false);

        H2 title = new H2(Tr.t("Cont nou"));
        title.getStyle().set("margin-bottom", "0");
        card.add(title);

        if (!userService.isRegistrationEnabled()) {
            card.add(new Paragraph(Tr.t("Înregistrarea conturilor noi este dezactivată. Contactați administratorul pentru a primi un cont.")));
            card.add(new RouterLink(Tr.t("Înapoi la clasament"), PlayersView.class));
            add(card);
            return;
        }

        Paragraph intro = new Paragraph(Tr.t("Completați datele de mai jos. Veți apărea automat în lista jucătorilor; restul profilului îl puteți completa după autentificare."));
        intro.getStyle().set("color", "var(--lumo-secondary-text-color)");

        TextField lastName = new TextField(Tr.t("Nume"));
        TextField firstName = new TextField(Tr.t("Prenume"));
        TextField username = new TextField(Tr.t("Nume de utilizator"));
        username.setHelperText(Tr.t("Litere latine, cifre și . _ -"));
        PasswordField password = new PasswordField(Tr.t("Parolă"));
        password.setHelperText(Tr.t("Cel puțin {0} caractere", UserService.MIN_PASSWORD_LENGTH));
        PasswordField confirm = new PasswordField(Tr.t("Confirmați parola"));

        RegistrationForm form = new RegistrationForm();
        BeanValidationBinder<RegistrationForm> binder = new BeanValidationBinder<>(RegistrationForm.class);
        binder.forField(lastName).asRequired(Tr.t("Introduceți numele")).bind("lastName");
        binder.forField(firstName).asRequired(Tr.t("Introduceți prenumele")).bind("firstName");
        binder.forField(username).asRequired(Tr.t("Introduceți numele de utilizator")).bind("username");
        binder.forField(password).asRequired(Tr.t("Introduceți parola")).bind("password");
        binder.forField(confirm)
                .asRequired(Tr.t("Confirmați parola"))
                .withValidator(value -> value.equals(password.getValue()), Tr.t("Parolele nu coincid"))
                .bind(f -> "", (f, v) -> { });
        binder.readBean(form);

        FormLayout layout = new FormLayout(lastName, firstName, username, password, confirm);
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("24rem", 2));
        layout.setColspan(username, 2);

        Button create = new Button(Tr.t("Creează contul"), e -> {
            if (binder.writeBeanIfValid(form)) {
                try {
                    userService.register(form);
                    Notifications.success(Tr.t("Contul a fost creat. Vă puteți autentifica."));
                    e.getSource().getUI().ifPresent(ui -> ui.navigate(LoginView.class));
                } catch (BusinessException ex) {
                    Notifications.error(ex.getLocalizedMessage());
                }
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setWidthFull();

        RouterLink toLogin = new RouterLink(Tr.t("Aveți deja cont? Autentificați-vă"), LoginView.class);
        toLogin.getStyle().set("font-size", "var(--lumo-font-size-s)");

        card.add(intro, layout, create, toLogin);
        add(card);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SecurityUtils.isAuthenticated()) {
            event.forwardTo(PlayersView.class);
        }
    }
}
