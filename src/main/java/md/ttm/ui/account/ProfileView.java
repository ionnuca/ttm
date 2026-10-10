package md.ttm.ui.account;

import md.ttm.common.BusinessException;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.model.user.AppUser;
import md.ttm.service.player.PlayerService;
import md.ttm.security.SecurityUtils;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.service.rating.RatingService;
import md.ttm.ui.player.PhotoEditor;
import md.ttm.ui.player.PlayerView;
import com.vaadin.flow.router.RouterLink;
import md.ttm.service.user.UserService;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.player.RatingHistoryList;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.PlayerDetailsFields;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.textfield.IntegerField;
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

/**
 * Pagina utilizatorului logat: datele proprii de jucător și schimbarea parolei.
 * Ratingul și statisticile sunt doar afișate; administratorul își poate stabili aici ratingul inițial.
 */
@Route(value = "profil", layout = MainLayout.class)
@PageTitle("Profilul meu | TTM")
@PermitAll
public class ProfileView extends VerticalLayout {

    private final PlayerService playerService;
    private final UserService userService;
    private final RatingService ratingService;
    private final Span ratingValue = new Span();
    private RatingHistoryList history;

    public ProfileView(PlayerService playerService, UserService userService, RatingService ratingService,
                       PlayerPhotoService photoService) {
        this.playerService = playerService;
        this.userService = userService;
        this.ratingService = ratingService;
        setMaxWidth("44rem");

        AppUser user = userService.findCurrentUser().orElseThrow();

        H2 title = new H2("Profilul meu");
        title.getStyle().set("margin-bottom", "0");
        Span account = new Span("Cont: " + user.getUsername() + " · " + user.getRole().getLabel());
        account.getStyle().set("color", "var(--lumo-secondary-text-color)");
        add(title, account);

        if (user.getPlayer() != null) {
            history = new RatingHistoryList(ratingService.findHistory(user.getPlayer().getId()),
                    user.getPlayer().getInitialRating());
            RouterLink publicPage = new RouterLink("Pagina mea de jucător (statistici și meciuri) →",
                    PlayerView.class, user.getPlayer().getId());
            add(publicPage, new PhotoEditor(user.getPlayer(), photoService, null),
                    createStats(user.getPlayer()), createPlayerForm(user.getPlayer()), history);
        } else {
            add(new Paragraph("Acest cont nu are un profil de jucător."));
        }
        add(createPasswordForm());
    }

    private Component createStats(Player player) {
        HorizontalLayout stats = new HorizontalLayout(
                stat("Rating", ratingValue, String.valueOf(player.getRating())),
                stat("Victorii", new Span(), String.valueOf(player.getWins())),
                stat("Înfrângeri", new Span(), String.valueOf(player.getLosses())));
        stats.getStyle().set("flex-wrap", "wrap");
        return stats;
    }

    private static Component stat(String label, Span valueSpan, String value) {
        valueSpan.setText(value);
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
        PlayerDetailsFields details = new PlayerDetailsFields(binder);
        IntegerField initialRating = null;
        if (SecurityUtils.isAdmin()) {
            // Doar administratorul își poate stabili ratingul inițial; ratingul curent se recalculează
            initialRating = new IntegerField("Rating inițial");
            initialRating.setMin(0);
            initialRating.setMax(5000);
            initialRating.setStep(10);
            initialRating.setStepButtonsVisible(true);
            initialRating.setHelperText("Punctul de plecare; ratingul curent se recalculează din el și din turneele încheiate");
            binder.forField(initialRating).asRequired("Introduceți ratingul inițial").bind("initialRating");
        }
        binder.readBean(player);

        FormLayout form = new FormLayout(lastName, firstName, playStyle);
        details.addPlayHandTo(form);
        form.add(city, phone);
        if (initialRating != null) {
            form.add(initialRating);
        }
        details.addEquipmentTo(form);
        form.setResponsiveSteps(
                new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));

        Button save = new Button("Salvează profilul", e -> {
            if (binder.writeBeanIfValid(player)) {
                try {
                    Player saved = playerService.updateOwnProfile(player);
                    binder.readBean(saved);
                    refreshRating(saved);
                    Notifications.success("Profil salvat");
                } catch (RuntimeException ex) {
                    Notifications.error(Notifications.saveError(ex));
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        VerticalLayout section = new VerticalLayout(new H3("Date personale"), form, save);
        section.setPadding(false);
        return section;
    }

    /** După schimbarea ratingului inițial: ratingul curent și istoricul, fără reîncărcarea paginii. */
    private void refreshRating(Player saved) {
        ratingValue.setText(String.valueOf(saved.getRating()));
        RatingHistoryList updated = new RatingHistoryList(ratingService.findHistory(saved.getId()),
                saved.getInitialRating());
        replace(history, updated);
        history = updated;
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
