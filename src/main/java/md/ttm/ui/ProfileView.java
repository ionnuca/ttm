package md.ttm.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.PasswordField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.BeanValidationBinder;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.PermitAll;
import md.ttm.common.BusinessException;
import md.ttm.player.PlayStyle;
import md.ttm.player.Player;
import md.ttm.player.PlayerService;
import md.ttm.user.AppUser;
import md.ttm.user.UserService;

/**
 * Pagina utilizatorului logat: datele proprii de jucător și schimbarea parolei.
 * Ratingul și statisticile sunt doar afișate; le gestionează administratorul (ulterior, rezultatele meciurilor).
 */
@Route(value = "profil", layout = MainLayout.class)
@PageTitle("Profilul meu | TTM")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final PlayerService playerService;
    private final UserService userService;

    public ProfileView(PlayerService playerService, UserService userService) {
        this.playerService = playerService;
        this.userService = userService;
        setMaxWidth("44rem");

        AppUser user = userService.findCurrentUser().orElseThrow();

        H2 title = new H2("Profilul meu");
        title.getStyle().set("margin-bottom", "0");
        Span account = new Span("Cont: " + user.getUsername() + " · " + user.getRole().getLabel());
        account.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(title, account);

        if (user.getPlayer() != null) {
            add(createStats(user.getPlayer()), createPlayerForm(user.getPlayer()));
        } else {
            add(new Paragraph("Acest cont nu are un profil de jucător."));
        }
        add(createPasswordForm());
    }

    private Component createStats(Player player) {
        HorizontalLayout stats = new HorizontalLayout(
                stat("Rating", String.valueOf(player.getRating())),
                stat("Victorii", String.valueOf(player.getWins())),
                stat("Înfrângeri", String.valueOf(player.getLosses())));
        stats.getStyle().set("flex-wrap", "wrap");
        return stats;
    }

    private static Component stat(String label, String value) {
        Span valueSpan = new Span(value);
        valueSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-xxl)")
                .set("font-weight", "600")
                .set("line-height", "1.2");
        Span labelSpan = new Span(label);
        labelSpan.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        Div box = new Div(valueSpan, labelSpan);
        box.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("min-width", "7rem")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)");
        return box;
    }

    private Component createPlayerForm(Player player) {
        TextField lastName = new TextField("Nume");
        TextField firstName = new TextField("Prenume");
        RadioButtonGroup<PlayStyle> playStyle = new RadioButtonGroup<>("Stil de joc");
        playStyle.setItems(PlayStyle.values());
        playStyle.setItemLabelGenerator(PlayStyle::getLabel);
        TextField city = new TextField("Oraș");
        TextField phone = new TextField("Telefon");
        phone.setPlaceholder("+373 ...");
        phone.setHelperText("Vizibil doar administratorului");

        BeanValidationBinder<Player> binder = new BeanValidationBinder<>(Player.class);
        binder.forField(lastName).asRequired("Introduceți numele").bind("lastName");
        binder.forField(firstName).asRequired("Introduceți prenumele").bind("firstName");
        binder.forField(playStyle).asRequired("Alegeți stilul de joc").bind("playStyle");
        binder.forField(city).bind("city");
        binder.forField(phone).bind("phone");
        binder.readBean(player);

        FormLayout form = new FormLayout(lastName, firstName, playStyle, city, phone);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(playStyle, 2);

        Button save = new Button("Salvează profilul", e -> {
            if (binder.writeBeanIfValid(player)) {
                try {
                    Player saved = playerService.updateOwnProfile(player);
                    binder.readBean(saved);
                    Notifications.success("Profil salvat");
                } catch (RuntimeException ex) {
                    Notifications.error(PlayersView.saveErrorMessage(ex));
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout section = new VerticalLayout(new H3("Date personale"), form, save);
        section.setPadding(false);
        return section;
    }

    private Component createPasswordForm() {
        PasswordField current = new PasswordField("Parola actuală");
        PasswordField newPassword = new PasswordField("Parola nouă");
        newPassword.setHelperText("Cel puțin " + UserService.MIN_PASSWORD_LENGTH + " caractere");
        PasswordField confirm = new PasswordField("Confirmați parola nouă");

        FormLayout form = new FormLayout(current, newPassword, confirm);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        form.setColspan(current, 2);

        Button change = new Button("Schimbă parola", e -> {
            if (!newPassword.getValue().equals(confirm.getValue())) {
                confirm.setInvalid(true);
                confirm.setErrorMessage("Parolele nu coincid");
                return;
            }
            confirm.setInvalid(false);
            try {
                userService.changeOwnPassword(current.getValue(), newPassword.getValue());
                current.clear();
                newPassword.clear();
                confirm.clear();
                Notifications.success("Parola a fost schimbată");
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            }
        });

        VerticalLayout section = new VerticalLayout(new H3("Schimbarea parolei"), form, change);
        section.setPadding(false);
        return section;
    }
}
