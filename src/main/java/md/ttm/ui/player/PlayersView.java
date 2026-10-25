package md.ttm.ui.player;

import md.ttm.i18n.Tr;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import md.ttm.security.SecurityUtils;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.service.player.PlayerService;
import md.ttm.ui.components.PlayerAvatar;
import md.ttm.service.player.RankedPlayer;
import md.ttm.service.tournament.Podium;
import md.ttm.service.tournament.TournamentService;
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
import com.vaadin.flow.router.HasDynamicTitle;
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
@Route(value = "jucatori", layout = MainLayout.class)
@AnonymousAllowed
public class PlayersView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Jucători") + " | TTM";
    }


    private final PlayerService playerService;
    private final boolean admin;
    private final Grid<RankedPlayer> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span counter = new Span();
    private final Span tapHint = new Span(Tr.t("Apăsați pe un jucător pentru detalii, statistici și meciuri."));
    private final PlayerPhotoService photoService;
    private final TournamentService tournamentService;
    private Map<Long, Long> photoVersions = Map.of();
    private Map<Long, Podium> podiums = Map.of();
    private List<RankedPlayer> players = List.of();
    private boolean narrowScreen;

    public PlayersView(PlayerService playerService, PlayerPhotoService photoService, TournamentService tournamentService) {
        this.playerService = playerService;
        this.photoService = photoService;
        this.tournamentService = tournamentService;
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
        H2 title = new H2(Tr.t("Clasament jucători"));
        title.getStyle().set("margin", "0");
        counter.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        return header;
    }

    private Component createToolbar() {
        search.setPlaceholder(Tr.t("Caută după nume sau oraș"));
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
            Button add = new Button(Tr.t("Adaugă jucător"), VaadinIcon.PLUS.create(), e -> openEditor(new Player()));
            add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            toolbar.add(add);
        }
        return toolbar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        Grid.Column<RankedPlayer> rank = grid.addColumn(RankedPlayer::rank)
                .setHeader(Tr.t("Loc"))
                .setWidth("4.5rem").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparingInt(RankedPlayer::rank));

        Comparator<RankedPlayer> byName = Comparator.comparing(
                (RankedPlayer rp) -> rp.player().getDisplayName(), String.CASE_INSENSITIVE_ORDER);

        Grid.Column<RankedPlayer> name = grid.addComponentColumn(this::nameCell)
                .setHeader(Tr.t("Nume Prenume"))
                .setAutoWidth(true)
                .setFlexGrow(3)
                .setSortable(true)
                .setComparator(byName);

        // Pe telefon: avatarul și numele, pe mai multe rânduri dacă e nevoie
        Grid.Column<RankedPlayer> compact = grid.addComponentColumn(this::compactPlayerCell)
                .setHeader(Tr.t("Jucător"))
                .setWidth("10rem").setFlexGrow(1)
                .setSortable(true)
                .setComparator(byName);
        compact.setVisible(false);

        Grid.Column<RankedPlayer> rating = grid.addColumn(rp -> rp.player().getRating())
                .setHeader(Tr.t("Rating"))
                .setWidth("5.5rem").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparingInt((RankedPlayer rp) -> rp.player().getRating()));

        Grid.Column<RankedPlayer> winLoss = grid.addColumn(rp -> rp.player().getWins() + " / " + rp.player().getLosses())
                .setHeader(Tr.t("Victorii / Înfrângeri"))
                .setWidth("11rem").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER)
                .setSortable(true)
                .setComparator(Comparator.comparingInt((RankedPlayer rp) -> rp.player().getWins()));

        List<Grid.Column<RankedPlayer>> podiumColumns = List.of(
                podiumColumn("🥇", Tr.t("Locul 1 în turnee"), Podium::first),
                podiumColumn("🥈", Tr.t("Locul 2 în turnee"), Podium::second),
                podiumColumn("🥉", Tr.t("Locul 3 în turnee"), Podium::third),
                podiumColumn("F2", Tr.t("Câștigător al Finalei 2"), Podium::final2));

        Grid.Column<RankedPlayer> phone = null;
        Grid.Column<RankedPlayer> actions = null;
        if (admin) {
            phone = grid.addColumn(rp -> valueOrDash(rp.player().getPhone()))
                    .setHeader(Tr.t("Telefon"))
                    .setAutoWidth(true).setFlexGrow(0);

            actions = grid.addComponentColumn(this::createActions)
                    .setHeader(Tr.t("Acțiuni"))
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
            rank.setWidth(narrow ? "2.5rem" : "4.5rem");
            rank.setHeader(narrow ? "#" : Tr.t("Loc"));
            if (actionsColumn != null) {
                actionsColumn.setVisible(!narrow);
            }
            name.setVisible(!narrow);
            if (phoneColumn != null) {
                phoneColumn.setVisible(!narrow);
            }
            compact.setVisible(narrow);
            winLoss.setHeader(narrow ? Tr.t("V / Î") : Tr.t("Victorii / Înfrângeri"));
            winLoss.setWidth(narrow ? "5.5rem" : "11rem");
            rating.setWidth(narrow ? "4rem" : "5.5rem");
            podiumColumns.forEach(c -> c.setWidth(narrow ? "2.75rem" : "3.5rem"));
            // pe telefon coloanele nu încap toate: locul și numele rămân fixe, restul se derulează lateral
            rank.setFrozen(narrow);
            compact.setFrozen(narrow);
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

        name.getStyle().set("white-space", "normal").set("line-height", "1.25");
        HorizontalLayout row = new HorizontalLayout(
                PlayerAvatar.of(player, photoVersions.get(player.getId()), "2rem"), name);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        return row;
    }

    /** O coloană cu numărul de locuri 1, 2, 3 sau de Finale 2 câștigate; 0 apare estompat. */
    private Grid.Column<RankedPlayer> podiumColumn(String header, String description,
                                                   java.util.function.ToIntFunction<Podium> value) {
        java.util.function.ToIntFunction<RankedPlayer> of =
                rp -> value.applyAsInt(podiums.getOrDefault(rp.player().getId(), Podium.NONE));
        Span title = new Span(header);
        title.setTitle(description);
        title.getElement().setAttribute("aria-label", description);
        return grid.addComponentColumn(rp -> {
                    int n = of.applyAsInt(rp);
                    Span cell = new Span(String.valueOf(n));
                    if (n == 0) {
                        cell.getStyle().set("color", "var(--lumo-disabled-text-color)");
                    } else {
                        cell.getStyle().set("font-weight", "600");
                    }
                    return cell;
                })
                .setHeader(title)
                .setWidth("3.5rem").setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.CENTER)
                .setSortable(true)
                .setComparator(Comparator.comparingInt(of));
    }

    private Component createActions(RankedPlayer rankedPlayer) {
        Player player = rankedPlayer.player();

        Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditor(player));
        edit.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        edit.setAriaLabel(Tr.t("Editează {0}", player.getDisplayName()));
        edit.setTooltipText(Tr.t("Editează"));

        Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(player));
        delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel(Tr.t("Șterge {0}", player.getDisplayName()));
        delete.setTooltipText(Tr.t("Șterge"));

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.setSpacing(false);
        return actions;
    }

    private void openEditor(Player player) {
        Runnable onDelete = player.getId() == null ? null : () -> confirmDelete(player);
        new PlayerFormDialog(player, edited -> {
            playerService.save(edited);
            Notifications.success(player.getId() == null ? Tr.t("Jucător adăugat") : Tr.t("Modificări salvate"));
            refresh();
        }, onDelete).open();
    }

    private void confirmDelete(Player player) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Ștergeți jucătorul?"));
        dialog.setText(Tr.t("{0} va fi șters definitiv, împreună cu contul lui de utilizator, dacă are unul.", player.getDisplayName()));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Șterge"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                playerService.delete(player.getId());
                Notifications.success(Tr.t("Jucător șters"));
            } catch (BusinessException ex) {
                Notifications.error(ex.getLocalizedMessage());
            }
            refresh();
        });
        dialog.open();
    }

    private void refresh() {
        players = playerService.findRanked();
        photoVersions = photoService.versions();
        podiums = tournamentService.podiums();
        applyFilter();
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<RankedPlayer> filtered = term.isEmpty()
                ? players
                : players.stream().filter(rp -> matches(rp.player(), term)).toList();
        grid.setItems(filtered);
        counter.setText(filtered.size() == players.size()
                ? Tr.t("{0} jucători", players.size())
                : Tr.t("{0} din {1} jucători", filtered.size(), players.size()));
    }

    private static boolean matches(Player player, String term) {
        return player.getDisplayName().toLowerCase(Locale.ROOT).contains(term)
                || (player.getFirstName() + " " + player.getLastName()).toLowerCase(Locale.ROOT).contains(term)
                || (player.getCity() != null && player.getCity().toLowerCase(Locale.ROOT).contains(term));
    }


    private static String valueOrDash(String value) {
        return value == null || value.isBlank() ? "—" : value;
    }
}
