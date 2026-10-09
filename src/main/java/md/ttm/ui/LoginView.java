package md.ttm.ui;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.security.SecurityUtils;
import md.ttm.user.UserService;

/**
 * Pagina de autentificare. Formularul trimite datele la {@code /login}, procesat de Spring Security.
 */
@Route(value = "login", layout = MainLayout.class)
@PageTitle("Autentificare | TTM")
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver {

    private final LoginForm login = new LoginForm();

    public LoginView(UserService userService) {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        getStyle().set("padding-top", "var(--lumo-space-xl)");

        login.setAction("login");
        login.setI18n(romanian());
        login.setForgotPasswordButtonVisible(false);
        add(login);

        Div links = new Div();
        links.getStyle()
                .set("display", "flex")
                .set("gap", "var(--lumo-space-l)")
                .set("font-size", "var(--lumo-font-size-s)");
        if (userService.isRegistrationEnabled()) {
            links.add(new RouterLink("Nu aveți cont? Înregistrați-vă", RegisterView.class));
        }
        links.add(new RouterLink("Înapoi la clasament", PlayersView.class));
        add(links);
    }

    @Override
    public void beforeEnter(BeforeEnterEvent event) {
        if (SecurityUtils.isAuthenticated()) {
            event.forwardTo(PlayersView.class);
            return;
        }
        if (event.getLocation().getQueryParameters().getParameters().containsKey("error")) {
            login.setError(true);
        }
    }

    private static LoginI18n romanian() {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle("Autentificare");
        form.setUsername("Nume de utilizator");
        form.setPassword("Parolă");
        form.setSubmit("Intră în cont");
        form.setForgotPassword("Ați uitat parola?");
        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle("Autentificare eșuată");
        error.setMessage("Verificați numele de utilizator și parola, apoi încercați din nou.");
        return i18n;
    }
}
