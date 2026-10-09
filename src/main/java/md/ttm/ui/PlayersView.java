package md.ttm.ui;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
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
import md.ttm.common.BusinessException;
import md.ttm.player.PlayStyle;
import md.ttm.player.Player;
import md.ttm.player.PlayerService;
import md.ttm.player.RankedPlayer;
import md.ttm.security.SecurityUtils;
import org.springframework.orm.ObjectOptimisticLockingFailureException;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

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
    private final Span tapHint = new Span("Atingeți un jucător pentru a-l edita sau șterge.");
    private List<RankedPlayer> players = List.of();
    private boolean narrowScreen;

    public PlayersView(PlayerService playerService) {
        this.playerService = playerService;
        this.admin = SecurityUtils.isAdmin();

        setSizeFull();
        setPadding(true);
        tapHint.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        tapHint.setVisible(false);
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

        Grid.Column<RankedPlayer> name = grid.addColumn(rp -> rp.player().getDisplayName())
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

        // Pe telefon, administratorul editează atingând rândul (butoanele nu încap)
        grid.addItemClickListener(e -> {
            if (admin && narrowScreen) {
                openEditor(e.getItem().player());
            }
        });

        Grid.Column<RankedPlayer> phoneColumn = phone;
        Grid.Column<RankedPlayer> actionsColumn = actions;
        Responsive.onNarrowChange(this, narrow -> {
            narrowScreen = narrow;
            rank.setWidth(narrow ? "3rem" : "4.5rem");
            rank.setHeader(narrow ? "#" : "Loc");
            if (actionsColumn != null) {
                actionsColumn.setVisible(!narrow);
            }
            tapHint.setVisible(admin && narrow);
            name.setVisible(!narrow);
            style.setVisible(!narrow);
            city.setVisible(!narrow);
            if (phoneColumn != null) {
                phoneColumn.setVisible(!narrow);
            }
            compact.setVisible(narrow);
            winLoss.setHeader(narrow ? "V / Î" : "Victorii / Înfrângeri");
            grid.recalculateColumnWidths();
        });
    }

    private Component compactPlayerCell(RankedPlayer rankedPlayer) {
        Player player = rankedPlayer.player();
        Span name = new Span(player.getDisplayName());
        name.getStyle().set("font-weight", "500");

        StringBuilder details = new StringBuilder(player.getPlayStyle().getLabel());
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
        return cell;
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

    static Component playStyleBadge(PlayStyle style) {
        Span badge = new Span(style.getLabel());
        boolean attack = style == PlayStyle.ATTACK;
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "0 var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("line-height", "1.6")
                .set("color", attack ? "var(--lumo-error-text-color)" : "var(--lumo-primary-text-color)")
                .set("background", attack ? "var(--lumo-error-color-10pct)" : "var(--lumo-primary-color-10pct)");
        return badge;
    }

    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }

    /** Folosit de dialog pentru a semnala conflictele de editare simultană. */
    static String saveErrorMessage(RuntimeException ex) {
        if (ex instanceof ObjectOptimisticLockingFailureException) {
            return "Datele au fost modificate între timp de altcineva. Reîncărcați pagina și încercați din nou.";
        }
        return ex.getMessage();
    }
}
