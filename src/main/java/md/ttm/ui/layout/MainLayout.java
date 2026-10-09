package md.ttm.ui.layout;

import md.ttm.model.user.AppUser;
import md.ttm.security.SecurityUtils;
import md.ttm.service.user.UserService;
import md.ttm.ui.account.LoginView;
import md.ttm.ui.account.ProfileView;
import md.ttm.ui.account.RegisterView;
import md.ttm.ui.components.Responsive;
import md.ttm.ui.player.PlayersView;
import md.ttm.ui.tournament.TournamentsView;
import md.ttm.ui.user.UsersView;
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

    public MainLayout(AuthenticationContext authenticationContext, UserService userService) {
        setPrimarySection(Section.DRAWER);
        Component title = createTitle();
        hiddenWhenNarrow.add(title);
        addToNavbar(new DrawerToggle(), title, createUserArea(authenticationContext, userService));
        addToDrawer(createDrawerHeader(), new Scroller(createNavigation()));
        Responsive.onNarrowChange(this, narrow -> hiddenWhenNarrow.forEach(c -> c.setVisible(!narrow)));
    }

    private static Component createTitle() {
        Span title = new Span("Tenis de masă");
        title.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", "600")
                .set("white-space", "nowrap");
        return title;
    }

    private static Component createDrawerHeader() {
        Span logo = new Span("TTM");
        logo.getStyle()
                .set("font-size", "var(--lumo-font-size-xl)")
                .set("font-weight", "700")
                .set("color", "var(--lumo-primary-text-color)");
        Span subtitle = new Span("Clasament și turnee");
        subtitle.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        Div header = new Div(logo, subtitle);
        header.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("padding", "var(--lumo-space-m) var(--lumo-space-l)");
        return header;
    }

    private static SideNav createNavigation() {
        SideNav nav = new SideNav();
        nav.addItem(new SideNavItem("Jucători", PlayersView.class, VaadinIcon.TROPHY.create()));
        nav.addItem(new SideNavItem("Turnee", TournamentsView.class, VaadinIcon.FLAG_CHECKERED.create()));
        if (SecurityUtils.isAuthenticated()) {
            nav.addItem(new SideNavItem("Profilul meu", ProfileView.class, VaadinIcon.USER.create()));
        }
        if (SecurityUtils.isAdmin()) {
            nav.addItem(new SideNavItem("Utilizatori", UsersView.class, VaadinIcon.USERS.create()));
        }
        return nav;
    }

    private Component createUserArea(AuthenticationContext authenticationContext, UserService userService) {
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
                user.setText(name + " · Administrator");
            }
            hiddenWhenNarrow.add(user);
            Button logout = new Button("Ieșire", VaadinIcon.SIGN_OUT.create(), e -> authenticationContext.logout());
            logout.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            area.add(user, logout);
        } else {
            Button login = new Button("Autentificare", VaadinIcon.SIGN_IN.create(),
                    e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(LoginView.class)));
            login.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            area.add(login);
            if (userService.isRegistrationEnabled()) {
                Button register = new Button("Cont nou",
                        e -> e.getSource().getUI().ifPresent(ui -> ui.navigate(RegisterView.class)));
                register.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                hiddenWhenNarrow.add(register);
                area.add(register);
            }
        }
        return area;
    }
}
