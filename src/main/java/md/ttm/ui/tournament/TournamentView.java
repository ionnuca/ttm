package md.ttm.ui.tournament;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.H4;
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
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.security.SecurityUtils;
import md.ttm.service.tournament.PrizePlace;
import md.ttm.service.tournament.TournamentDetails;
import md.ttm.service.tournament.TournamentService;
import md.ttm.ui.account.LoginView;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.layout.MainLayout;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Pagina unui turneu.
 * <ul>
 *     <li>Înainte de începere: lista participanților, înscriere / retragere, pornirea turneului.</li>
 *     <li>După începere: tabelul-matrice cu rezultatele și lista meciurilor pe tururi,
 *     cu introducerea rezultatelor.</li>
 * </ul>
 */
@Route(value = "turneu", layout = MainLayout.class)
@AnonymousAllowed
public class TournamentView extends VerticalLayout implements HasUrlParameter<Long>, HasDynamicTitle {

    private final TournamentService tournamentService;
    private Long tournamentId;
    private String title = "Turneu | TTM";

    public TournamentView(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
        setMaxWidth("80rem");
        setWidthFull();
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        this.tournamentId = id;
        refresh();
    }

    @Override
    public String getPageTitle() {
        return title;
    }

    private void refresh() {
        removeAll();
        RouterLink back = new RouterLink("← Toate turneele", TournamentsView.class);
        back.getStyle().set("font-size", "var(--lumo-font-size-s)");
        add(back);

        TournamentDetails details = tournamentService.findDetails(tournamentId).orElse(null);
        if (details == null) {
            add(new H2("Turneul nu există"), new Paragraph("Este posibil să fi fost șters."));
            return;
        }
        title = details.tournament().getName() + " | TTM";
        add(createHeader(details));
        if (details.prizePool() != null) {
            add(createPrizes(details));
        }
        if (details.tournament().isStarted()) {
            add(createStandings(details), createMatches(details));
        } else {
            add(createRegistration(details));
        }
    }

    // ------------------------------------------------------------------
    // Antet
    // ------------------------------------------------------------------

    private Component createHeader(TournamentDetails details) {
        Tournament t = details.tournament();
        H2 name = new H2(t.getName());
        name.getStyle().set("margin", "0");

        HorizontalLayout titleRow = new HorizontalLayout(name);
        titleRow.setWidthFull();
        titleRow.setAlignItems(FlexComponent.Alignment.CENTER);
        titleRow.getStyle().set("flex-wrap", "wrap");
        if (details.admin()) {
            Div spacer = new Div();
            spacer.getStyle().set("flex-grow", "1");
            titleRow.add(spacer);
            if (t.isRegistrationOpen()) {
                Button edit = new Button("Editează", VaadinIcon.EDIT.create(), e -> openEditDialog(t));
                edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                titleRow.add(edit);
            }
            Button delete = new Button("Șterge", VaadinIcon.TRASH.create(), e -> confirmDelete(t));
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            titleRow.add(delete);
        }

        String configuration = t.isStarted()
                ? t.getFormat().getLabel() + " · best of " + t.getBestOf() + " · "
                : "";
        Span meta = new Span(TournamentLabels.longDate(t.getTournamentDate()) + " · "
                + configuration + details.participants().size() + " participanți");
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout metaRow = new HorizontalLayout(meta, TournamentLabels.statusBadge(t.getStatus()));
        metaRow.setAlignItems(FlexComponent.Alignment.CENTER);
        metaRow.getStyle().set("flex-wrap", "wrap");

        VerticalLayout header = new VerticalLayout(titleRow, metaRow);
        header.setPadding(false);
        header.setSpacing(false);
        return header;
    }

    private Component createPrizes(TournamentDetails details) {
        Tournament t = details.tournament();
        Div card = card();

        Span title = new Span("Turneu comercial");
        title.getStyle().set("font-weight", "600");
        Span summary = new Span("Taxa de participare: " + TournamentLabels.money(t.getEntryFee())
                + " · Suma acumulată: " + TournamentLabels.money(details.prizePool())
                + " (" + details.participants().size() + " participanți)");
        summary.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Div places = new Div();
        places.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-s) var(--lumo-space-l)")
                .set("margin-top", "var(--lumo-space-s)");
        for (PrizePlace prize : details.prizes()) {
            Span place = new Span("Locul " + prize.place() + ": ");
            place.getStyle().set("color", "var(--lumo-secondary-text-color)");
            Span amount = new Span(TournamentLabels.money(prize.amount()) + " (" + prize.percentage() + "%)");
            amount.getStyle().set("font-weight", "600");
            Span item = new Span(place, amount);
            if (prize.winnerName() != null) {
                Span winner = new Span(" — " + prize.winnerName());
                item.add(winner);
            }
            places.add(item);
        }
        card.add(title, new Div(summary), places);
        return card;
    }

    // ------------------------------------------------------------------
    // Înainte de începere: înscrierea
    // ------------------------------------------------------------------

    private Component createRegistration(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Participanți înscriși (" + details.participants().size() + ")"));

        HorizontalLayout actions = new HorizontalLayout();
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.getStyle().set("flex-wrap", "wrap");
        if (details.canSelfRegister()) {
            Button register = new Button("Mă înscriu", VaadinIcon.PLUS.create(),
                    e -> run(() -> tournamentService.registerSelf(tournamentId), "V-ați înscris la turneu"));
            register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(register);
        }
        if (details.canSelfUnregister()) {
            Button leave = new Button("Mă retrag", VaadinIcon.MINUS.create(),
                    e -> run(() -> tournamentService.unregisterSelf(tournamentId), "V-ați retras de la turneu"));
            actions.add(leave);
        }
        if (details.admin()) {
            ComboBox<Player> player = new ComboBox<>("Adaugă un jucător");
            player.setItems(tournamentService.findPlayersNotRegistered(tournamentId));
            player.setItemLabelGenerator(p -> p.getDisplayName() + " (" + p.getRating() + ")");
            player.setPlaceholder("Alegeți jucătorul");
            player.setWidth("min(100%, 20rem)");
            Button add = new Button("Adaugă", e -> {
                if (player.getValue() == null) {
                    player.setInvalid(true);
                    player.setErrorMessage("Alegeți un jucător");
                    return;
                }
                run(() -> tournamentService.addParticipant(tournamentId, player.getValue().getId()),
                        player.getValue().getDisplayName() + " a fost înscris");
            });
            actions.add(player, add);

            Div spacer = new Div();
            spacer.getStyle().set("flex-grow", "1");
            Button start = new Button("Începe turneul", VaadinIcon.PLAY.create(), e -> openStartDialog(details));
            start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            start.setEnabled(details.participants().size() >= 2);
            actions.add(spacer, start);
            actions.setWidthFull();
        }
        if (actions.getComponentCount() > 0) {
            section.add(actions);
        }
        if (!SecurityUtils.isAuthenticated()) {
            Paragraph hint = new Paragraph();
            hint.add(new Span("Pentru a vă înscrie, "), new RouterLink("autentificați-vă", LoginView.class), new Span("."));
            section.add(hint);
        }

        if (details.participants().isEmpty()) {
            section.add(new Paragraph("Încă nu s-a înscris nimeni."));
            return section;
        }
        Grid<TournamentParticipant> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);
        List<TournamentParticipant> participants = details.participants();
        grid.addColumn(p -> participants.indexOf(p) + 1)
                .setHeader("#").setWidth("3.5rem").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(p -> p.getPlayer().getDisplayName()).setHeader("Nume Prenume").setFlexGrow(2);
        grid.addColumn(p -> p.getPlayer().getRating()).setHeader("Rating")
                .setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(p -> p.getPlayer().getCity() != null ? p.getPlayer().getCity() : "—").setHeader("Oraș");
        if (details.admin()) {
            grid.addComponentColumn(p -> {
                Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> run(
                        () -> tournamentService.removeParticipant(tournamentId, p.getId()),
                        p.getPlayer().getDisplayName() + " a fost scos din turneu"));
                remove.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                remove.setAriaLabel("Scoate " + p.getPlayer().getDisplayName());
                remove.setTooltipText("Scoate din turneu");
                return remove;
            }).setAutoWidth(true).setFlexGrow(0);
        }
        grid.setItems(participants);
        Span order = new Span("Ordinea e după rating; la începerea turneului devine ordinea din grupă. "
                + "Tipul turneului, numărul de seturi și opțiunea de turneu comercial se aleg la „Începe turneul”.");
        order.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        section.add(grid, order);
        return section;
    }

    private void openStartDialog(TournamentDetails details) {
        new StartTournamentDialog(details.tournament().getName(), details.participants().size(), settings -> {
            tournamentService.start(tournamentId, settings);
            Notifications.success("Turneul a început");
            refresh();
        }).open();
    }

    // ------------------------------------------------------------------
    // După începere: tabelul și meciurile
    // ------------------------------------------------------------------

    private Component createStandings(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3("Tabelul turneului");
        title.getStyle().set("margin-bottom", "0");
        long played = details.playedMatches();
        Span progress = new Span("Meciuri jucate: " + played + " din " + details.matches().size());
        progress.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout heading = new HorizontalLayout(title, progress);
        heading.setAlignItems(FlexComponent.Alignment.BASELINE);
        heading.getStyle().set("flex-wrap", "wrap");

        Long own = details.ownParticipant() != null ? details.ownParticipant().getId() : null;
        Span legend = new Span("În fiecare celulă: sus — punctele (2 victorie, 1 înfrângere, 0 înfrângere tehnică), "
                + "jos — scorul la seturi. W — victorie tehnică, L — înfrângere tehnică.");
        legend.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        section.add(heading, new ResultsMatrix(details.participants(), details.standings(), own), legend);
        return section;
    }

    private Component createMatches(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3("Meciuri"));
        if (!details.canRecordResults() && details.tournament().getStatus() == TournamentStatus.IN_PROGRESS) {
            Span hint = new Span(SecurityUtils.isAuthenticated()
                    ? "Rezultatele le introduc participanții turneului și administratorul."
                    : "Rezultatele le introduc participanții turneului, după autentificare.");
            hint.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            section.add(hint);
        }

        Map<Integer, List<TournamentMatch>> rounds = details.matches().stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRoundNo, TreeMap::new, Collectors.toList()));
        Div roundsLayout = new Div();
        roundsLayout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(min(100%, 22rem), 1fr))")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%");
        Long own = details.ownParticipant() != null ? details.ownParticipant().getId() : null;
        rounds.forEach((round, matches) -> {
            Div roundCard = card();
            H4 roundTitle = new H4("Turul " + round);
            roundTitle.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
            roundCard.add(roundTitle);
            matches.forEach(match -> roundCard.add(matchRow(details, match, own)));
            roundsLayout.add(roundCard);
        });
        section.add(roundsLayout);
        return section;
    }

    private Component matchRow(TournamentDetails details, TournamentMatch match, Long own) {
        TournamentParticipant a = match.getParticipantA();
        TournamentParticipant b = match.getParticipantB();
        boolean aWon = match.isPlayed() && match.getWinner().getId().equals(a.getId());
        boolean bWon = match.isPlayed() && match.getWinner().getId().equals(b.getId());

        Span nameA = matchName(a, aWon, true);
        Span nameB = matchName(b, bWon, false);
        Span result;
        if (!match.isPlayed()) {
            result = new Span("–:–");
            result.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        } else if (match.isWalkover()) {
            result = Badges.badge(aWon ? "W : L" : "L : W", Badges.Tone.ERROR);
            result.getElement().setAttribute("title", "Victorie tehnică");
        } else {
            result = new Span(match.getSetsA() + " : " + match.getSetsB());
            result.getStyle().set("font-weight", "700");
        }
        result.getStyle().set("min-width", "3.5rem").set("text-align", "center");

        HorizontalLayout row = new HorizontalLayout(nameA, result, nameB);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setSpacing(true);
        row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
        boolean ownMatch = own != null && (own.equals(a.getId()) || own.equals(b.getId()));
        if (ownMatch) {
            row.getStyle().set("background", "var(--lumo-primary-color-10pct)").set("border-radius", "var(--lumo-border-radius-s)");
        }

        if (details.canRecordResults()) {
            Button enter = new Button(match.isPlayed() ? VaadinIcon.EDIT.create() : VaadinIcon.PLUS.create(),
                    e -> openResultDialog(details, match));
            enter.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            String label = (match.isPlayed() ? "Modifică rezultatul " : "Introdu rezultatul ")
                    + a.getPlayer().getDisplayName() + " – " + b.getPlayer().getDisplayName();
            enter.setAriaLabel(label);
            enter.setTooltipText(match.isPlayed() ? "Modifică rezultatul" : "Introdu rezultatul");
            row.add(enter);
        }
        return row;
    }

    private static Span matchName(TournamentParticipant participant, boolean winner, boolean alignRight) {
        Span name = new Span(participant.getPlayer().getDisplayName());
        name.getStyle()
                .set("flex", "1")
                .set("min-width", "0")
                .set("text-align", alignRight ? "right" : "left")
                .set("font-weight", winner ? "600" : "400");
        return name;
    }

    private void openResultDialog(TournamentDetails details, TournamentMatch match) {
        Runnable onClear = details.admin()
                ? () -> {
                    tournamentService.clearResult(match.getId());
                    Notifications.success("Rezultat șters");
                    refresh();
                }
                : null;
        new MatchResultDialog(match, details.tournament().getSetsToWin(), result -> {
            tournamentService.recordResult(match.getId(), result);
            Notifications.success("Rezultat salvat");
            refresh();
        }, onClear).open();
    }

    // ------------------------------------------------------------------
    // Administrare
    // ------------------------------------------------------------------

    private void openEditDialog(Tournament tournament) {
        new TournamentFormDialog(tournament, form -> {
            tournamentService.update(tournamentId, form);
            Notifications.success("Turneu actualizat");
            refresh();
        }).open();
    }

    private void confirmDelete(Tournament tournament) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Ștergeți turneul?");
        dialog.setText("„" + tournament.getName() + "” va fi șters definitiv, împreună cu înscrierile și "
                + "toate rezultatele lui.");
        dialog.setCancelable(true);
        dialog.setCancelText("Anulează");
        dialog.setConfirmText("Șterge");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                tournamentService.delete(tournamentId);
                Notifications.success("Turneu șters");
                getUI().ifPresent(ui -> ui.navigate(TournamentsView.class));
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            }
        });
        dialog.open();
    }

    /** Execută o acțiune, afișează rezultatul și reîncarcă pagina. */
    private void run(Runnable action, String successMessage) {
        try {
            action.run();
            Notifications.success(successMessage);
        } catch (RuntimeException ex) {
            Notifications.error(Notifications.saveError(ex));
        }
        refresh();
    }

    private static Div card() {
        Div card = new Div();
        card.getStyle()
                .set("padding", "var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("box-sizing", "border-box")
                .set("width", "100%");
        return card;
    }
}
