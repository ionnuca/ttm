package md.ttm.ui.player;

import md.ttm.common.BusinessException;
import md.ttm.model.player.PlayStyle;
import md.ttm.model.player.Player;
import md.ttm.security.SecurityUtils;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.service.player.PlayerService;
import md.ttm.ui.components.PlayerAvatar;
import md.ttm.service.player.RankedPlayer;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.Responsive;
import md.ttm.ui.layout.MainLayout;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pagina principală: lista jucătorilor ordonată după rating.
 * <ul>
 *     <li>Guest și Utilizator: doar vizualizare.</li>
 *     <li>Administrator: vede și telefonul; poate adăuga, edita și șterge jucători.</li>
 * </ul>
 */
@Route(value = "", layout = MainLayout.class)
@PageTitle("Jucători | TTM")
@AnonymousAllowed
public class PlayersView extends VerticalLayout {

    private final PlayerService playerService;
    private final boolean admin;
    private final Grid<RankedPlayer> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span counter = new Span();
    private final Span tapHint = new Span("Apăsați pe un jucător pentru detalii, statistici și meciuri.");
    private final PlayerPhotoService photoService;
    private Map<Long, Long> photoVersions = Map.of();
    private List<RankedPlayer> players = List.of();
    private boolean narrowScreen;

    public PlayersView(PlayerService playerService, PlayerPhotoService photoService) {
        this.playerService = playerService;
        this.photoService = photoService;
        this.admin = SecurityUtils.isAdmin();

        setSizeFull();
        setPadding(true);
        tapHint.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        add(createHeader(), createToolbar(), tapHint, grid);
        configureGrid();
        refresh();
    }

    private Component createHeader() {
        H2 title = new H2("Clasament jucători");
        title.getStyle().set("margin", "0");
        counter.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        return header;
    }

    private Component createToolbar() {
        search.setPlaceholder("Caută după nume sau oraș");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> applyFilter());
        search.setWidth("min(100%, 22rem)");

        HorizontalLayout toolbar = new HorizontalLayout(search);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("flex-wrap", "wrap");

        if (admin) {
            Button add = new Button("Adaugă jucător", VaadinIcon.PLUS.create(), e -> openEditor(new Player()));
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            toolbar.add(add);
        }
        return toolbar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        Grid.Column<RankedPlayer> rank = grid.addColumn(RankedPlayer::rank)
                .setHeader("Loc")
                .setWidth("4.5rem").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparingInt(RankedPlayer::rank));

        Comparator<RankedPlayer> byName = Comparator.comparing(
                (RankedPlayer rp) -> rp.player().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

        Grid.Column<RankedPlayer> name = grid.addComponentColumn(this::nameCell)
                .setHeader("Nume Prenume")
                .setFlexGrow(3)
                .setSortable(true)
                .setComparator(byName);

        // Pe telefon: o singură coloană cu numele și, dedesubt, stilul, orașul (și telefonul pentru admin)
        Grid.Column<RankedPlayer> compact = grid.addComponentColumn(this::compactPlayerCell)
                .setHeader("Jucător")
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(byName);
        compact.setVisible(false);

        Grid.Column<RankedPlayer> style = grid.addComponentColumn(rp -> playStyleBadge(rp.player().getPlayStyle()))
                .setHeader("Stil de joc")
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing((RankedPlayer rp) -> rp.player().getPlayStyle()));

        Grid.Column<RankedPlayer> hand = grid.addColumn(rp -> handLabel(rp.player()))
                .setHeader("Mâna")
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing((RankedPlayer rp) -> handLabel(rp.player())));

        Grid.Column<RankedPlayer> city = grid.addColumn(rp -> valueOrDash(rp.player().getCity()))
                .setHeader("Oraș")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(
                        (RankedPlayer rp) -> valueOrDash(rp.player().getCity()), String.CASE_INSENSITIVE_ORDER));

        grid.addColumn(rp -> rp.player().getRating())
                .setHeader("Rating")
                .setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparingInt((RankedPlayer rp) -> rp.player().getRating()));

        Grid.Column<RankedPlayer> winLoss = grid.addColumn(rp -> rp.player().getWins() + " / " + rp.player().getLosses())
                .setHeader("Victorii / Înfrângeri")
                .setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER)
                .setSortable(true)
                .setComparator(Comparator.comparingInt((RankedPlayer rp) -> rp.player().getWins()));

        Grid.Column<RankedPlayer> phone = null;
        Grid.Column<RankedPlayer> actions = null;
        if (admin) {
            phone = grid.addColumn(rp -> valueOrDash(rp.player().getPhone()))
                    .setHeader("Telefon")
                    .setAutoWidth(true).setFlexGrow(0);

            actions = grid.addComponentColumn(this::createActions)
                    .setHeader("Acțiuni")
                    .setAutoWidth(true).setFlexGrow(0)
                    .setFrozenToEnd(true);
        }

        // Click pe rând: pagina jucătorului (detalii, statistici, meciuri; administratorul îl editează de acolo)
        Grid.Column<RankedPlayer> phoneColumn = phone;
        Grid.Column<RankedPlayer> actionsColumn = actions;
        grid.addItemClickListener(e -> {
            if (actionsColumn == null || e.getColumn() != actionsColumn) {
                getUI().ifPresent(ui -> ui.navigate(PlayerView.class, e.getItem().player().getId()));
            }
        });
        Responsive.onNarrowChange(this, narrow -> {
            narrowScreen = narrow;
            rank.setWidth(narrow ? "3rem" : "4.5rem");
            rank.setHeader(narrow ? "#" : "Loc");
            if (actionsColumn != null) {
                actionsColumn.setVisible(!narrow);
            }
            name.setVisible(!narrow);
            style.setVisible(!narrow);
            hand.setVisible(!narrow);
            city.setVisible(!narrow);
            if (phoneColumn != null) {
                phoneColumn.setVisible(!narrow);
            }
            compact.setVisible(narrow);
            winLoss.setHeader(narrow ? "V / Î" : "Victorii / Înfrângeri");
            grid.recalculateColumnWidths();
        });
    }

    private Component nameCell(RankedPlayer rankedPlayer) {
        Player player = rankedPlayer.player();
        HorizontalLayout cell = new HorizontalLayout(
                PlayerAvatar.of(player, photoVersions.get(player.getId()), "2rem"), new Span(player.getDisplayName()));
        cell.setAlignItems(FlexComponent.Alignment.CENTER);
        cell.setSpacing(true);
        return cell;
    }

    private Component compactPlayerCell(RankedPlayer rankedPlayer) {
        Player player = rankedPlayer.player();
        Span name = new Span(player.getDisplayName());
        name.getStyle().set("font-weight", "500");

        StringBuilder details = new StringBuilder(player.getPlayStyle().getLabel());
        if (player.getPlayHand() != null) {
            details.append(" · ").append(player.getPlayHand().getLabel());
        }
        if (player.getCity() != null) {
            details.append(" · ").append(player.getCity());
        }
        Span secondary = secondaryText(details.toString());
        VerticalLayout cell = new VerticalLayout(name, secondary);
        if (admin && player.getPhone() != null) {
            cell.add(secondaryText(player.getPhone()));
        }
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.getStyle().set("line-height", "1.3").set("white-space", "normal");
        HorizontalLayout row = new HorizontalLayout(
                PlayerAvatar.of(player, photoVersions.get(player.getId()), "2.25rem"), cell);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        return row;
    }

    private static String handLabel(Player player) {
        return player.getPlayHand() != null ? player.getPlayHand().getLabel() : "—";
    }

    private static Span secondaryText(String text) {
        Span span = new Span(text);
        span.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        return span;
    }

    private Component createActions(RankedPlayer rankedPlayer) {
        Player player = rankedPlayer.player();

        Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditor(player));
        edit.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        edit.setAriaLabel("Editează " + player.getDisplayName());
        edit.setTooltipText("Editează");

        Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(player));
        delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel("Șterge " + player.getDisplayName());
        delete.setTooltipText("Șterge");

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.setSpacing(false);
        return actions;
    }

    private void openEditor(Player player) {
        Runnable onDelete = player.getId() == null ? null : () -> confirmDelete(player);
        new PlayerFormDialog(player, edited -> {
            playerService.save(edited);
            Notifications.success(player.getId() == null ? "Jucător adăugat" : "Modificări salvate");
            refresh();
        }, onDelete).open();
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
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            }
            refresh();
        });
        dialog.open();
    }

    private void refresh() {
        players = playerService.findRanked();
        photoVersions = photoService.versions();
        applyFilter();
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<RankedPlayer> filtered = term.isEmpty()
                ? players
                : players.stream().filter(rp -> matches(rp.player(), term)).toList();
        grid.setItems(filtered);
        counter.setText(filtered.size() == players.size()
                ? players.size() + " jucători"
                : filtered.size() + " din " + players.size() + " jucători");
    }

    private static boolean matches(Player player, String term) {
        return player.getDisplayName().toLowerCase(Locale.ROOT).contains(term)
                || (player.getFirstName() + " " + player.getLastName()).toLowerCase(Locale.ROOT).contains(term)
                || (player.getCity() != null && player.getCity().toLowerCase(Locale.ROOT).contains(term));
    }

    private static Component playStyleBadge(PlayStyle style) {
        return Badges.badge(style.getLabel(), style == PlayStyle.ATTACK ? Badges.Tone.ERROR : Badges.Tone.PRIMARY);
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
