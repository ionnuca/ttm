package md.ttm.ui.player;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.BeforeEvent;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.HasUrlParameter;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.security.SecurityUtils;
import md.ttm.service.match.MatchService;
import md.ttm.service.match.PlayerProfile;
import md.ttm.service.match.PlayerStats;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.service.player.PlayerService;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.PlayerAvatar;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.match.MatchList;

/**
 * Pagina unui jucător: date, echipament, statistica meciurilor, evoluția ratingului și lista meciurilor.
 * Publică; telefonul îl vede doar administratorul, care poate și edita jucătorul.
 */
@Route(value = "jucator", layout = MainLayout.class)
@AnonymousAllowed
public class PlayerView extends VerticalLayout implements HasUrlParameter<Long>, HasDynamicTitle {

    private final MatchService matchService;
    private final PlayerService playerService;
    private final PlayerPhotoService photoService;
    private Long playerId;
    private String title = "Jucător | TTM";

    public PlayerView(MatchService matchService, PlayerService playerService, PlayerPhotoService photoService) {
        this.matchService = matchService;
        this.playerService = playerService;
        this.photoService = photoService;
        setMaxWidth("56rem");
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        playerId = id;
        refresh();
    }

    @Override
    public String getPageTitle() {
        return title;
    }

    private void refresh() {
        removeAll();
        add(new RouterLink("← Clasament", PlayersView.class));
        PlayerProfile profile = playerId == null ? null : matchService.findPlayerProfile(playerId).orElse(null);
        if (profile == null) {
            add(new H2("Jucătorul nu există"), new Paragraph("Poate a fost șters între timp."));
            return;
        }
        Player player = profile.player();
        title = player.getDisplayName() + " | TTM";

        add(createHeader(profile), createStats(profile.stats(), player, profile));
        if (photoService.canEdit(player.getId())) {
            add(new PhotoEditor(player, photoService, this::refresh));
        }
        add(createEquipment(player),
                new RatingHistoryList(profile.ratingHistory(), player.getInitialRating()));

        H3 matchesTitle = new H3("Meciuri (" + profile.matches().size() + ")");
        add(matchesTitle);
        if (profile.matches().isEmpty()) {
            add(new Paragraph("Încă nu are meciuri cu rezultat."));
        } else {
            add(new MatchList(profile.matches(), player.getId()));
        }
    }

    private Component createHeader(PlayerProfile profile) {
        Player player = profile.player();
        H2 name = new H2(player.getDisplayName());
        name.getStyle().set("margin", "0");

        StringBuilder details = new StringBuilder("Locul " + profile.rank() + " din " + profile.rankedPlayers());
        if (player.getPlayStyle() != null) {
            details.append(" · ").append(player.getPlayStyle().getLabel());
        }
        if (player.getPlayHand() != null) {
            details.append(" · mâna ").append(player.getPlayHand().getLabel().toLowerCase());
        }
        if (player.getCity() != null) {
            details.append(" · ").append(player.getCity());
        }
        Span line = secondary(details.toString());
        VerticalLayout text = new VerticalLayout(name, line);
        if (SecurityUtils.isAdmin() && player.getPhone() != null) {
            text.add(secondary("Telefon: " + player.getPhone()));
        }
        text.setPadding(false);
        text.setSpacing(false);

        HorizontalLayout header = new HorizontalLayout(
                PlayerAvatar.of(player, photoService.version(player.getId()), "4.5rem"), text);
        header.setAlignItems(FlexComponent.Alignment.CENTER);
        header.setWidthFull();
        if (SecurityUtils.isAdmin()) {
            Div spacer = new Div();
            spacer.getStyle().set("flex-grow", "1");
            Button edit = new Button("Editează", VaadinIcon.EDIT.create(), e -> openEditor(player));
            edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            header.add(spacer, edit);
        }
        header.getStyle().set("flex-wrap", "wrap");
        return header;
    }

    private Component createStats(PlayerStats stats, Player player, PlayerProfile profile) {
        Div tiles = new Div();
        tiles.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(7.5rem, 1fr))")
                .set("gap", "var(--lumo-space-s)")
                .set("width", "100%");
        tiles.add(tile("Rating", String.valueOf(player.getRating()), "locul " + profile.rank()),
                tile("Cel mai bun rating", String.valueOf(stats.bestRating()), null),
                tile("Meciuri jucate", String.valueOf(stats.played()), null),
                tile("Victorii", String.valueOf(stats.wins()),
                        stats.walkoverWins() > 0 ? "+ " + stats.walkoverWins() + " tehnice" : null),
                tile("Înfrângeri", String.valueOf(stats.losses()),
                        stats.walkoverLosses() > 0 ? "+ " + stats.walkoverLosses() + " tehnice" : null),
                tile("Victorii %", stats.winPercent() == null ? "–" : stats.winPercent() + "%", null),
                tile("Seturi", stats.setsWon() + " / " + stats.setsLost(), "câștigate / pierdute"),
                tile("Turnee", String.valueOf(stats.tournaments()), null));
        Span note = secondary("Statistica include toate meciurile cu rezultat, și din turneele în desfășurare; "
                + "ratingul se modifică doar la încheierea turneului.");
        note.getStyle().set("font-size", "var(--lumo-font-size-xs)");
        VerticalLayout section = new VerticalLayout(tiles, note);
        section.setPadding(false);
        section.setSpacing(false);
        return section;
    }

    private static Component tile(String label, String value, String hint) {
        Span valueSpan = new Span(value);
        valueSpan.getStyle().set("font-size", "var(--lumo-font-size-xl)").set("font-weight", "600")
                .set("line-height", "1.2");
        Div box = new Div(valueSpan, secondary(label));
        if (hint != null) {
            Span h = secondary(hint);
            h.getStyle().set("font-size", "var(--lumo-font-size-xs)");
            box.add(h);
        }
        box.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)");
        return box;
    }

    private static Component createEquipment(Player player) {
        VerticalLayout section = new VerticalLayout(new H3("Echipament"));
        section.setPadding(false);
        section.setSpacing(false);
        if (!player.hasEquipment()) {
            section.add(secondary("Echipamentul nu este completat."));
            return section;
        }
        section.add(item("Lemn", player.getBlade()), item("Forehand", player.getForehandRubber()),
                item("Backhand", player.getBackhandRubber()));
        return section;
    }

    private static Component item(String label, String value) {
        Span valueSpan = new Span(value == null ? "—" : value);
        valueSpan.getStyle().set("font-weight", "500");
        return new Span(secondary(label + ": "), valueSpan);
    }

    private static Span secondary(String text) {
        Span span = new Span(text);
        span.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        return span;
    }

    private void openEditor(Player player) {
        new PlayerFormDialog(player, edited -> {
            playerService.save(edited);
            Notifications.success("Modificări salvate");
            refresh();
        }, () -> confirmDelete(player)).open();
    }

    private void confirmDelete(Player player) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Ștergeți jucătorul?");
        dialog.setText(player.getDisplayName() + " va fi șters definitiv, împreună cu contul lui de "
                + "utilizator, dacă are unul.");
        dialog.setCancelable(true);
        dialog.setCancelText("Anulează");
        dialog.setConfirmText("Șterge");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                playerService.delete(player.getId());
                Notifications.success("Jucător șters");
                getUI().ifPresent(ui -> ui.navigate(PlayersView.class));
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            }
        });
        dialog.open();
    }
}
