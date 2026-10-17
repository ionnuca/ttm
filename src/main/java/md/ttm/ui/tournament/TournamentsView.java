package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.model.tournament.Tournament;
import md.ttm.security.SecurityUtils;
import md.ttm.service.tournament.TournamentService;
import md.ttm.service.tournament.TournamentSummary;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.Responsive;
import md.ttm.ui.layout.MainLayout;

import java.util.Comparator;

/**
 * Lista turneelor. Administratorul și managerul de turnee pot crea un turneu nou; click pe un turneu deschide pagina lui.
 */
@Route(value = "turnee", layout = MainLayout.class)
@AnonymousAllowed
public class TournamentsView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Turnee") + " | TTM";
    }


    private final TournamentService tournamentService;
    private final Grid<TournamentSummary> grid = new Grid<>();
    private final Span counter = new Span();

    public TournamentsView(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
        setSizeFull();
        add(createHeader());
        if (SecurityUtils.canManageTournaments()) {
            Button create = new Button(Tr.t("Turneu nou"), VaadinIcon.PLUS.create(), e -> openCreateDialog());
            create.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            add(create);
        }
        add(grid);
        configureGrid();
        refresh();
    }

    private Component createHeader() {
        H2 title = new H2(Tr.t("Turnee"));
        title.getStyle().set("margin", "0");
        counter.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        return header;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES);

        Grid.Column<TournamentSummary> date = grid.addColumn(s -> TournamentLabels.shortDate(s.tournament().getTournamentDate()))
                .setHeader(Tr.t("Data"))
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing((TournamentSummary s) -> s.tournament().getTournamentDate()));
        Grid.Column<TournamentSummary> name = grid.addColumn(s -> s.tournament().getName())
                .setHeader(Tr.t("Turneu"))
                .setFlexGrow(3)
                .setSortable(true)
                .setComparator(Comparator.comparing((TournamentSummary s) -> s.tournament().getName(),
                        String.CASE_INSENSITIVE_ORDER));
        Grid.Column<TournamentSummary> compact = grid.addComponentColumn(this::compactCell)
                .setHeader(Tr.t("Turneu"))
                .setFlexGrow(1);
        compact.setVisible(false);
        Grid.Column<TournamentSummary> config = grid.addColumn(s -> s.tournament().isStarted()
                        ? TournamentLabels.configuration(s.tournament())
                        : "—")
                .setHeader(Tr.t("Format"))
                .setAutoWidth(true).setFlexGrow(0);
        Grid.Column<TournamentSummary> participants = grid.addColumn(s -> s.pendingRequests() > 0
                        ? Tr.t("{0} (+{1} cereri)", s.participants(), s.pendingRequests())
                        : String.valueOf(s.participants()))
                .setHeader(Tr.t("Participanți"))
                .setAutoWidth(true).setFlexGrow(0)
                .setTextAlign(ColumnTextAlign.END)
                .setSortable(true)
                .setComparator(Comparator.comparingLong(TournamentSummary::participants));
        Grid.Column<TournamentSummary> type = grid.addColumn(s -> typeLabel(s.tournament()))
                .setHeader(Tr.t("Tip"))
                .setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(s -> TournamentLabels.statusBadge(s.tournament().getStatus()))
                .setHeader(Tr.t("Stare"))
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing((TournamentSummary s) -> s.tournament().getStatus()));

        grid.addItemClickListener(e -> grid.getUI().ifPresent(ui ->
                ui.navigate(TournamentView.class, e.getItem().tournament().getId())));

        Responsive.onNarrowChange(this, narrow -> {
            date.setVisible(!narrow);
            name.setVisible(!narrow);
            config.setVisible(!narrow);
            participants.setVisible(!narrow);
            type.setVisible(!narrow);
            compact.setVisible(narrow);
            grid.recalculateColumnWidths();
        });
    }

    private Component compactCell(TournamentSummary summary) {
        Tournament t = summary.tournament();
        Span name = new Span(t.getName());
        name.getStyle().set("font-weight", "500");
        Span details = new Span(TournamentLabels.shortDate(t.getTournamentDate()) + " · "
                + Tr.t("{0} participanți", summary.participants())
                + (summary.pendingRequests() > 0 ? " · " + Tr.t("{0} cereri de confirmat", summary.pendingRequests()) : "")
                + (t.isCommercial() ? " · " + typeLabel(t) : ""));
        details.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout cell = new VerticalLayout(name, details);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.getStyle().set("line-height", "1.3").set("white-space", "normal");
        return cell;
    }

    private static String typeLabel(Tournament tournament) {
        if (!tournament.isStarted()) {
            return "—";
        }
        return tournament.isCommercial()
                ? Tr.t("Comercial · {0}", TournamentLabels.money(tournament.getEntryFee()))
                : Tr.t("Amical");
    }

    private void openCreateDialog() {
        new TournamentFormDialog(null, form -> {
            Tournament created = tournamentService.create(form);
            Notifications.success(Tr.t("Turneu creat"));
            getUI().ifPresent(ui -> ui.navigate(TournamentView.class, created.getId()));
        }).open();
    }

    private void refresh() {
        var tournaments = tournamentService.findAll();
        grid.setItems(tournaments);
        counter.setText(tournaments.size() == 1 ? Tr.t("1 turneu") : Tr.t("{0} turnee", tournaments.size()));
    }
}
