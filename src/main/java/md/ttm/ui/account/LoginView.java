package md.ttm.ui.account;

import md.ttm.i18n.Tr;
import md.ttm.security.SecurityUtils;
import md.ttm.service.user.UserService;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.PlayersView;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.login.LoginForm;
import com.vaadin.flow.component.login.LoginI18n;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.BeforeEnterObserver;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;

/**
 * Pagina de autentificare. Formularul trimite datele la {@code /login}, procesat de Spring Security.
 */
@Route(value = "login", layout = MainLayout.class)
@AnonymousAllowed
public class LoginView extends VerticalLayout implements BeforeEnterObserver, HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Autentificare") + " | TTM";
    }


    private final LoginForm login = new LoginForm();

    public LoginView(UserService userService) {
        setSizeFull();
        setAlignItems(FlexComponent.Alignment.CENTER);
        setJustifyContentMode(FlexComponent.JustifyContentMode.START);
        getStyle().set("padding-top", "var(--lumo-space-xl)");

        login.setAction("login");
        login.setI18n(translated());
        login.setForgotPasswordButtonVisible(false);
        add(login);

        Div links = new Div();
        links.getStyle()
                .set("display", "flex")
                .set("gap", "var(--lumo-space-l)")
                .set("font-size", "var(--lumo-font-size-s)");
        if (userService.isRegistrationEnabled()) {
            links.add(new RouterLink(Tr.t("Nu aveți cont? Înregistrați-vă"), RegisterView.class));
        }
        links.add(new RouterLink(Tr.t("Înapoi la clasament"), PlayersView.class));
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

    private static LoginI18n translated() {
        LoginI18n i18n = LoginI18n.createDefault();
        LoginI18n.Form form = i18n.getForm();
        form.setTitle(Tr.t("Autentificare"));
        form.setUsername(Tr.t("Nume de utilizator"));
        form.setPassword(Tr.t("Parolă"));
        form.setSubmit(Tr.t("Intră în cont"));
        form.setForgotPassword(Tr.t("Ați uitat parola?"));
        LoginI18n.ErrorMessage error = i18n.getErrorMessage();
        error.setTitle(Tr.t("Autentificare eșuată"));
        error.setMessage(Tr.t("Verificați numele de utilizator și parola, apoi încercați din nou."));
        return i18n;
    }
}
