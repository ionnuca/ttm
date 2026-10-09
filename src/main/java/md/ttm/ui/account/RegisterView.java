package md.ttm.ui.account;

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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Crearea simplă a unui cont, cu informații minime. Contul nou este de tip Utilizator
 * și are automat un profil de jucător cu ratingul inițial.
 */
@Route(value = "inregistrare", layout = MainLayout.class)
@PageTitle("Cont nou | TTM")
@AnonymousAllowed
public class RegisterView extends VerticalLayout implements BeforeEnterObserver {

    private final UserService userService;

    public RegisterView(UserService userService) {
        this.userService = userService;
        setAlignItems(FlexComponent.Alignment.CENTER);

        VerticalLayout card = new VerticalLayout();
        card.setMaxWidth("28rem");
        card.setPadding(false);

        H2 title = new H2("Cont nou");
        title.getStyle().set("margin-bottom", "0");
        card.add(title);

        if (!userService.isRegistrationEnabled()) {
            card.add(new Paragraph("Înregistrarea conturilor noi este dezactivată. "
                    + "Contactați administratorul pentru a primi un cont."));
            card.add(new RouterLink("Înapoi la clasament", PlayersView.class));
            add(card);
            return;
        }

        Paragraph intro = new Paragraph("Completați datele de mai jos. Veți apărea automat în lista "
                + "jucătorilor; restul profilului îl puteți completa după autentificare.");
        intro.getStyle().set("color", "var(--lumo-secondary-text-color)");

        TextField lastName = new TextField("Nume");
        TextField firstName = new TextField("Prenume");
        TextField username = new TextField("Nume de utilizator");
        username.setHelperText("Litere latine, cifre și . _ -");
        PasswordField password = new PasswordField("Parolă");
        password.setHelperText("Cel puțin " + UserService.MIN_PASSWORD_LENGTH + " caractere");
        PasswordField confirm = new PasswordField("Confirmați parola");

        RegistrationForm form = new RegistrationForm();
        BeanValidationBinder<RegistrationForm> binder = new BeanValidationBinder<>(RegistrationForm.class);
        binder.forField(lastName).asRequired("Introduceți numele").bind("lastName");
        binder.forField(firstName).asRequired("Introduceți prenumele").bind("firstName");
        binder.forField(username).asRequired("Introduceți numele de utilizator").bind("username");
        binder.forField(password).asRequired("Introduceți parola").bind("password");
        binder.forField(confirm)
                .asRequired("Confirmați parola")
                .withValidator(value -> value.equals(password.getValue()), "Parolele nu coincid")
                .bind(f -> "", (f, v) -> { });
        binder.readBean(form);

        FormLayout layout = new FormLayout(lastName, firstName, username, password, confirm);
        layout.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("24rem", 2));
        layout.setColspan(username, 2);

        Button create = new Button("Creează contul", e -> {
            if (binder.writeBeanIfValid(form)) {
                try {
                    userService.register(form);
                    Notifications.success("Contul a fost creat. Vă puteți autentifica.");
                    e.getSource().getUI().ifPresent(ui -> ui.navigate(LoginView.class));
                } catch (BusinessException ex) {
                    Notifications.error(ex.getMessage());
                }
            }
        });
        create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        create.setWidthFull();

        RouterLink toLogin = new RouterLink("Aveți deja cont? Autentificați-vă", LoginView.class);
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
