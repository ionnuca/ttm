package md.ttm.ui.layout;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.router.RouterLink;
import md.ttm.ui.help.HelpView;
import md.ttm.model.user.AppUser;
import md.ttm.ui.components.LanguageSwitcher;
import md.ttm.ui.components.NotificationBell;
import md.ttm.service.notification.NotificationService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.info.BuildProperties;
import md.ttm.model.user.Role;
import md.ttm.security.SecurityUtils;
import md.ttm.service.user.UserService;
import md.ttm.ui.account.LoginView;
import md.ttm.ui.account.ProfileView;
import md.ttm.ui.account.RegisterView;
import md.ttm.ui.components.Responsive;
import md.ttm.ui.components.Badges;
import org.springframework.beans.factory.annotation.Value;
import md.ttm.ui.player.PlayersView;
import md.ttm.ui.tournament.TournamentsView;
import md.ttm.ui.user.UsersView;
import md.ttm.ui.match.MatchesView;
import md.ttm.ui.admin.SettingsView;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.applayout.AppLayout;
import com.vaadin.flow.component.applayout.DrawerToggle;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.Scroller;
import com.vaadin.flow.component.sidenav.SideNav;
import com.vaadin.flow.component.sidenav.SideNavItem;
import com.vaadin.flow.spring.security.AuthenticationContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Structura comună a paginilor: bara de sus, meniul lateral și zona de conținut.
 * Elementele de meniu apar în funcție de rol.
 */
public class MainLayout extends AppLayout {

    /** Elemente ascunse pe telefon, ca bara de sus să încapă pe ecran. */
    private final List<Component> hiddenWhenNarrow = new ArrayList<>();

    public MainLayout(AuthenticationContext authenticationContext, UserService userService,
                      NotificationService notificationService,
                      ObjectProvider<BuildProperties> buildProperties,
                      @Value("${app.environment-label:}") String environmentLabel) {
        setPrimarySection(Section.DRAWER);
        Component title = createTitle();
        hiddenWhenNarrow.add(title);
        addToNavbar(new DrawerToggle(), title);
        if (environmentLabel != null && !environmentLabel.isBlank()) {
            // aplicația de test: eticheta vizibilă mereu, ca să nu fie confundată cu producția
            Span label = Badges.badge(environmentLabel, Badges.Tone.ERROR);
            label.getStyle().set("margin-left", "var(--lumo-space-s)").set("font-weight", "700");
            addToNavbar(label);
        }
        addToNavbar(createUserArea(authenticationContext, userService, notificationService));
        BuildProperties build = buildProperties.getIfAvailable();
        addToDrawer(createDrawerHeader(build == null ? null : build.getVersion()), new Scroller(createNavigation()));
        Responsive.onNarrowChange(this, narrow -> hiddenWhenNarrow.forEach(c -> c.setVisible(!narrow)));
    }

    /** Titlul din bara de sus; duce la pagina principală (meciurile). */
    private static Component createTitle() {
        RouterLink title = new RouterLink(Tr.t("Tenis de masă"), MatchesView.class);
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "600")
                .set("white-space", "nowrap")
                .set("color", "var(--lumo-body-text-color)")
                .set("text-decoration", "none");
        return title;
    }

    /** Pictograma și numele aplicației; apăsate, duc la pagina principală (meciurile). */
    private static Component createDrawerHeader(String version) {
        Image icon = new Image("icons/favicon.svg", "");
        icon.setWidth("2rem");
        icon.setHeight("2rem");
        Span name = new Span("TTM");
        name.getStyle()
                .set("font-size", "var(--lumo-font-size-xl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-primary-text-color)");
        RouterLink logo = new RouterLink(MatchesView.class);
        logo.add(icon, name);
        logo.getElement().setAttribute("aria-label", Tr.t("TTM – pagina principală"));
        logo.getStyle()
                .set("display", "inline-flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)")
                .set("text-decoration", "none")
                .set("align-self", "flex-start");
        Span subtitle = new Span(Tr.t("Clasament și turnee"));
        subtitle.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        Div header = new Div(logo, subtitle);
        if (version != null) {
            Span versionLabel = new Span(Tr.t("Versiunea {0}", version));
            versionLabel.getStyle()
                    .set("font-size", "var(--lumo-font-size-xs)")
                    .set("color", "var(--lumo-tertiary-text-color)");
            header.add(versionLabel);
        }
        header.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)");
        return header;
    }

    private static SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem(Tr.t("Meciuri"), MatchesView.class, VaadinIcon.LIST_UL.create()));
        nav.addItem(new SideNavItem(Tr.t("Jucători"), PlayersView.class, VaadinIcon.TROPHY.create()));
        nav.addItem(new SideNavItem(Tr.t("Turnee"), TournamentsView.class, VaadinIcon.FLAG_CHECKERED.create()));
        if (SecurityUtils.isAuthenticated()) {
            nav.addItem(new SideNavItem(Tr.t("Profilul meu"), ProfileView.class, VaadinIcon.USER.create()));
        }
        if (SecurityUtils.isAdmin()) {
            nav.addItem(new SideNavItem(Tr.t("Utilizatori"), UsersView.class, VaadinIcon.USERS.create()));
            nav.addItem(new SideNavItem(Tr.t("Setări"), SettingsView.class, VaadinIcon.COG.create()));
        }
        nav.addItem(new SideNavItem(Tr.t("Ghid de utilizare"), HelpView.class, VaadinIcon.QUESTION_CIRCLE_O.create()));
        return nav;
    }

    private Component createUserArea(AuthenticationContext authenticationContext, UserService userService,
                                     NotificationService notificationService) {
        HorizontalLayout area = new HorizontalLayout();
        area.setAlignItems(FlexComponent.Alignment.CENTER);
        area.setSpacing(true);
        area.getStyle()
                .set("margin-left", "auto")
                .set("padding-right", "var(--lumo-space-m)");

        if (SecurityUtils.isAuthenticated()) {
            String name = userService.findCurrentUser()
                    .map(AppUser::getDisplayName)
                    .orElseGet(() -> SecurityUtils.currentUsername().orElse(""));
            Span user = new Span(name);
            user.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            if (SecurityUtils.isAdmin()) {
                user.setText(name + " · " + Role.ADMIN.getLabel());
            } else if (SecurityUtils.hasRole(Role.TOURNAMENT_MANAGER)) {
                user.setText(name + " · " + Role.TOURNAMENT_MANAGER.getLabel());
            }
            hiddenWhenNarrow.add(user);
            Button logout = new Button(Tr.t("Ieșire"), VaadinIcon.SIGN_OUT.create(), e -> authenticationContext.logout());
            logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            area.add(new LanguageSwitcher(userService), new NotificationBell(notificationService), user, logout);
        } else {
            Button login = new Button(Tr.t("Autentificare"), VaadinIcon.SIGN_IN.create(),
                    e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            login.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            area.add(new LanguageSwitcher(userService), login);
            if (userService.isRegistrationEnabled()) {
                Button register = new Button(Tr.t("Cont nou"),
                        e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(RegisterView.class)));
                register.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                hiddenWhenNarrow.add(register);
                area.add(register);
            }
        }
        return area;
    }
}
