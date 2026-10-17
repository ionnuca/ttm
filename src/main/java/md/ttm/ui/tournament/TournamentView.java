package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
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
import md.ttm.model.tournament.TournamentTiebreak;
import md.ttm.service.tournament.StandingsCalculator;
import md.ttm.model.tournament.TournamentStage;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.security.SecurityUtils;
import md.ttm.service.tournament.GroupView;
import md.ttm.service.tournament.PrizePlace;
import md.ttm.service.tournament.TournamentDetails;
import md.ttm.service.tournament.TournamentService;
import md.ttm.ui.account.LoginView;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.RatingHistoryList;

import java.util.List;
import java.util.HashMap;
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

    /** Rândurile meciurilor, ca după salvarea unui rezultat pagina să rămână la meciul respectiv. */
    private final Map<Long, Component> matchRows = new HashMap<>();
    /** Casetele cu seturile de departajare, după grupă (0 = tabelul turneului Round Robin). */
    private final Map<Long, Component> tiebreakCards = new HashMap<>();

    private void refresh() {
        matchRows.clear();
        tiebreakCards.clear();
        removeAll();
        RouterLink back = new RouterLink(Tr.t("← Toate turneele"), TournamentsView.class);
        back.getStyle().set("font-size", "var(--lumo-font-size-s)");
        add(back);

        TournamentDetails details = tournamentService.findDetails(tournamentId).orElse(null);
        if (details == null) {
            add(new H2(Tr.t("Turneul nu există")), new Paragraph(Tr.t("Este posibil să fi fost șters.")));
            return;
        }
        title = details.tournament().getName() + " | TTM";
        add(createHeader(details));
        if (details.prizePool() != null) {
            add(createPrizes(details));
        }
        if (details.tournament().isStarted() && details.tournament().isGroupsFormat()) {
            addGroupsTournament(details);
        } else if (details.tournament().isStarted()) {
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
        if (details.manager()) {
            Div spacer = new Div();
            spacer.getStyle().set("flex-grow", "1");
            titleRow.add(spacer);
            if (t.isRegistrationOpen()) {
                Button edit = new Button(Tr.t("Editează"), VaadinIcon.EDIT.create(), e -> openEditDialog(t));
                edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                titleRow.add(edit);
            }
            if (details.canFinishManually()) {
                Button finish = new Button(Tr.t("Încheie turneul"), VaadinIcon.FLAG_CHECKERED.create(),
                        e -> confirmFinish(details));
                finish.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
                titleRow.add(finish);
            }
            Button delete = new Button(Tr.t("Șterge"), VaadinIcon.TRASH.create(), e -> confirmDelete(t));
            delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
            titleRow.add(delete);
        }

        String configuration = t.isStarted() ? TournamentLabels.configuration(t) + " · " : "";
        Span meta = new Span(TournamentLabels.longDate(t.getTournamentDate()) + " · "
                + configuration + Tr.t("{0} participanți", details.participants().size()));
        meta.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout metaRow = new HorizontalLayout(meta, TournamentLabels.statusBadge(t.getStatus()));
        if (t.isGroupsFormat() && t.getStage() != null && t.getStatus() == TournamentStatus.IN_PROGRESS) {
            metaRow.add(Badges.badge(t.getStage().getLabel(), Badges.Tone.CONTRAST));
        }
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

        Span title = new Span(Tr.t("Turneu comercial"));
        title.getStyle().set("font-weight", "600");
        Span summary = new Span(Tr.t("Taxa de participare: {0} · Suma acumulată: {1} ({2} participanți)",
                TournamentLabels.money(t.getEntryFee()), TournamentLabels.money(details.prizePool()),
                details.participants().size()));
        summary.getStyle().set("font-size", "var(--lumo-font-size-s)");

        Div places = new Div();
        places.getStyle()
                .set("display", "flex")
                .set("flex-wrap", "wrap")
                .set("gap", "var(--lumo-space-s) var(--lumo-space-l)")
                .set("margin-top", "var(--lumo-space-s)");
        for (PrizePlace prize : details.prizes()) {
            Span place = new Span(TournamentLabels.prizeTitle(prize.title()) + ": ");
            place.getStyle().set("color", "var(--lumo-secondary-text-color)");
            Span amount = new Span(TournamentLabels.money(prize.amount())
                    + (prize.percentage() != null ? " (" + prize.percentage() + "%)" : " " + Tr.t("(taxa de participare)")));
            amount.getStyle().set("font-weight", "600");
            Span item = new Span(place, amount);
            if (prize.winnerName() != null) {
                Span winner = new Span(" — " + prize.winnerName()
                        + (prize.shared() ? " " + Tr.t("(loc împărțit)") : ""));
                item.add(winner);
            }
            places.add(item);
        }
        card.add(title, new Div(summary), places);
        if (details.prizes().stream().anyMatch(PrizePlace::shared)) {
            card.add(new Div(hint(Tr.t("La un loc împărțit, premiile locurilor ocupate împreună se adună și se împart egal între jucători."))));
        }
        return card;
    }

    // ------------------------------------------------------------------
    // Înainte de începere: înscrierea
    // ------------------------------------------------------------------

    /** Cererile de înscriere neconfirmate, cu confirmare sau respingere (organizatori). */
    private Component pendingRequests(TournamentDetails details) {
        Div card = card();
        card.getStyle()
                .set("border-color", "var(--lumo-primary-color-50pct)")
                .set("background", "var(--lumo-primary-color-10pct)");
        H4 title = new H4(Tr.t("Cereri de înscriere ({0})", details.pendingRequests().size()));
        title.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
        card.add(title, hint(Tr.t("Jucătorii de mai jos au cerut înscrierea. Doar cei confirmați intră în turneu; cererile neconfirmate se anulează la începerea turneului.")));
        for (TournamentParticipant p : details.pendingRequests()) {
            Span name = new Span(p.getPlayer().getDisplayName() + " · " + Tr.t("rating {0}", p.getPlayer().getRating()));
            name.getStyle().set("flex", "1").set("min-width", "0");
            Button confirm = new Button(Tr.t("Confirmă"), VaadinIcon.CHECK.create(), e -> run(
                    () -> tournamentService.confirmParticipant(tournamentId, p.getId()),
                    Tr.t("Înscrierea lui {0} a fost confirmată", p.getPlayer().getDisplayName())));
            confirm.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);
            Button reject = new Button(Tr.t("Respinge"), VaadinIcon.CLOSE_SMALL.create(), e -> run(
                    () -> tournamentService.rejectParticipant(tournamentId, p.getId()),
                    Tr.t("Cererea lui {0} a fost respinsă", p.getPlayer().getDisplayName())));
            reject.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            HorizontalLayout row = new HorizontalLayout(name, confirm, reject);
            row.setWidthFull();
            row.setAlignItems(FlexComponent.Alignment.CENTER);
            row.getStyle()
                    .set("flex-wrap", "wrap")
                    .set("padding", "var(--lumo-space-xs) 0")
                    .set("border-top", "1px solid var(--lumo-contrast-10pct)");
            card.add(row);
        }
        return card;
    }

    private Component createRegistration(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(Tr.t("Participanți înscriși ({0})", details.participants().size())));

        HorizontalLayout actions = new HorizontalLayout();
        actions.setAlignItems(FlexComponent.Alignment.END);
        actions.getStyle().set("flex-wrap", "wrap");
        if (details.canSelfRegister()) {
            Button register = new Button(Tr.t("Mă înscriu"), VaadinIcon.PLUS.create(),
                    e -> {
                        try {
                            boolean confirmed = tournamentService.registerSelf(tournamentId).isConfirmed();
                            Notifications.success(confirmed
                                    ? Tr.t("V-ați înscris la turneu")
                                    : Tr.t("Cerere trimisă; veți fi anunțat după confirmare"));
                        } catch (RuntimeException ex) {
                            Notifications.error(Notifications.saveError(ex));
                        }
                        refresh();
                    });
            register.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(register);
        }
        if (details.ownRequestPending()) {
            Span pending = Badges.badge(Tr.t("Cererea de înscriere așteaptă confirmarea organizatorului"), Badges.Tone.PRIMARY);
            pending.getStyle().set("white-space", "normal");
            actions.add(pending);
        }
        if (details.canSelfUnregister()) {
            Button leave = new Button(details.ownRequestPending() ? Tr.t("Retrag cererea") : Tr.t("Mă retrag"),
                    VaadinIcon.MINUS.create(),
                    e -> run(() -> tournamentService.unregisterSelf(tournamentId), Tr.t("V-ați retras de la turneu")));
            actions.add(leave);
        }
        if (details.manager()) {
            ComboBox<Player> player = new ComboBox<>(Tr.t("Adaugă un jucător"));
            player.setItems(tournamentService.findPlayersNotRegistered(tournamentId));
            player.setItemLabelGenerator(p -> p.getDisplayName() + " (" + p.getRating() + ")");
            player.setPlaceholder(Tr.t("Alegeți jucătorul"));
            player.setWidth("min(100%, 20rem)");
            Button add = new Button(Tr.t("Adaugă"), e -> {
                if (player.getValue() == null) {
                    player.setInvalid(true);
                    player.setErrorMessage(Tr.t("Alegeți un jucător"));
                    return;
                }
                run(() -> tournamentService.addParticipant(tournamentId, player.getValue().getId()),
                        Tr.t("{0} a fost înscris", player.getValue().getDisplayName()));
            });
            actions.add(player, add);

            Div spacer = new Div();
            spacer.getStyle().set("flex-grow", "1");
            Button start = new Button(Tr.t("Începe turneul"), VaadinIcon.PLAY.create(), e -> openStartDialog(details));
            start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
            start.setEnabled(details.participants().size() >= 2);
            actions.add(spacer, start);
            actions.setWidthFull();
        }
        if (actions.getComponentCount() > 0) {
            section.add(actions);
        }
        if (details.manager() && !details.pendingRequests().isEmpty()) {
            section.add(pendingRequests(details));
        }
        if (!SecurityUtils.isAuthenticated()) {
            Paragraph hint = new Paragraph();
            hint.add(new Span(Tr.t("Pentru a vă înscrie,") + " "), new RouterLink(Tr.t("autentificați-vă"), LoginView.class), new Span("."));
            section.add(hint);
        }

        if (details.participants().isEmpty()) {
            section.add(new Paragraph(Tr.t("Încă nu s-a înscris nimeni.")));
            return section;
        }
        Grid<TournamentParticipant> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);
        List<TournamentParticipant> participants = details.participants();
        grid.addColumn(p -> participants.indexOf(p) + 1)
                .setHeader("#").setWidth("3.5rem").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(p -> p.getPlayer().getDisplayName()).setHeader(Tr.t("Nume Prenume")).setFlexGrow(2);
        grid.addColumn(p -> p.getPlayer().getRating()).setHeader(Tr.t("Rating"))
                .setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(p -> p.getPlayer().getCity() != null ? p.getPlayer().getCity() : "—").setHeader(Tr.t("Oraș"));
        if (details.manager()) {
            grid.addComponentColumn(p -> {
                Button remove = new Button(VaadinIcon.CLOSE_SMALL.create(), e -> run(
                        () -> tournamentService.removeParticipant(tournamentId, p.getId()),
                        Tr.t("{0} a fost scos din turneu", p.getPlayer().getDisplayName())));
                remove.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
                remove.setAriaLabel(Tr.t("Scoate {0}", p.getPlayer().getDisplayName()));
                remove.setTooltipText(Tr.t("Scoate din turneu"));
                return remove;
            }).setAutoWidth(true).setFlexGrow(0);
        }
        grid.setItems(participants);
        Span order = new Span(Tr.t("Ordinea e după rating; la începerea turneului devine ordinea din grupă. Tipul turneului, numărul de seturi și opțiunea de turneu comercial se aleg la „Începe turneul”."));
        order.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        section.add(grid, order);
        return section;
    }

    private void openStartDialog(TournamentDetails details) {
        new StartTournamentDialog(details.tournament().getName(), details.participants().size(), settings -> {
            tournamentService.start(tournamentId, settings);
            Notifications.success(Tr.t("Turneul a început"));
            refresh();
        }).open();
    }

    // ------------------------------------------------------------------
    // După începere: tabelul și meciurile
    // ------------------------------------------------------------------

    private Component createStandings(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3(Tr.t("Tabelul turneului"));
        title.getStyle().set("margin-bottom", "0");
        long played = details.playedMatches();
        Span progress = new Span(Tr.t("Meciuri jucate: {0} din {1}", played, details.matches().size()));
        progress.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout heading = new HorizontalLayout(title, progress);
        heading.setAlignItems(FlexComponent.Alignment.BASELINE);
        heading.getStyle().set("flex-wrap", "wrap");

        section.add(heading, new ResultsMatrix(details.participants(), details.standings(), ownId(details),
                details.ratingChanges()));
        Component tiebreaks = tiebreakSection(details, null, details.participants(), details.tiebreaks(),
                details.pendingTiebreaks(), details.canRecordResults());
        if (tiebreaks != null) {
            section.add(tiebreaks);
        }
        section.add(legend(details));
        return section;
    }

    private static Long ownId(TournamentDetails details) {
        return details.ownParticipant() != null ? details.ownParticipant().getId() : null;
    }

    private static Span legend(TournamentDetails details) {
        String ties = details.tournament().isCommercial()
                ? Tr.t("La egalitate de puncte decid meciurile directe; dacă egalitatea rămâne, jucătorii joacă între ei câte un set de departajare.")
                : Tr.t("La egalitate de puncte decid meciurile directe; dacă egalitatea rămâne, jucătorii împart locul (=).");
        Span legend = new Span(Tr.t("În fiecare celulă: sus — punctele (2 victorie, 1 înfrângere, 0 înfrângere tehnică), jos — scorul la seturi. W — victorie tehnică, L — înfrângere tehnică.")
                + " " + ties
                + (details.tournament().getStatus() == TournamentStatus.FINISHED
                ? " " + Tr.t("Sub nume: ratingul înainte → după turneu (Elo).") : ""));
        legend.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        return legend;
    }

    private Component createMatches(TournamentDetails details) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        section.add(new H3(Tr.t("Meciuri")));
        if (!details.canRecordResults() && details.tournament().getStatus() == TournamentStatus.IN_PROGRESS) {
            Span hint = new Span(SecurityUtils.isAuthenticated()
                    ? Tr.t("Rezultatele le introduc participanții turneului și organizatorii (administratorul, managerul de turnee).")
                    : Tr.t("Rezultatele le introduc participanții turneului, după autentificare."));
            hint.getStyle()
                    .set("font-size", "var(--lumo-font-size-s)")
                    .set("color", "var(--lumo-secondary-text-color)");
            section.add(hint);
        }

        section.add(rounds(details, details.matches(), details.canRecordResults()));
        return section;
    }

    /** Meciurile grupate pe tururi, câte un card pentru fiecare tur. */
    private Component rounds(TournamentDetails details, List<TournamentMatch> matches, boolean editable) {
        Map<Integer, List<TournamentMatch>> rounds = matches.stream()
                .collect(Collectors.groupingBy(TournamentMatch::getRoundNo, TreeMap::new, Collectors.toList()));
        Div roundsLayout = new Div();
        roundsLayout.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "repeat(auto-fill, minmax(min(100%, 22rem), 1fr))")
                .set("gap", "var(--lumo-space-m)")
                .set("width", "100%");
        Long own = ownId(details);
        rounds.forEach((round, roundMatches) -> {
            Div roundCard = card();
            H4 roundTitle = new H4(Tr.t("Turul {0}", round));
            roundTitle.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
            roundCard.add(roundTitle);
            roundMatches.forEach(match -> roundCard.add(matchRow(details, match, own, editable)));
            roundsLayout.add(roundCard);
        });
        return roundsLayout;
    }

    // ------------------------------------------------------------------
    // „Grupe + finale”
    // ------------------------------------------------------------------

    private void addGroupsTournament(TournamentDetails details) {
        Tournament t = details.tournament();
        if (details.canStartFinals()) {
            add(createStartFinalsCard(details));
        }
        if (!details.finals().isEmpty()) {
            add(stageTitle(TournamentStage.FINALS, details.finals()));
            for (GroupView group : details.finals()) {
                add(groupSection(details, group, details.canRecordResults()));
            }
        }
        add(stageTitle(TournamentStage.GROUPS, details.groupStage()));
        if (details.canRedrawGroups()) {
            Button redraw = new Button(Tr.t("Reîmparte grupele"), VaadinIcon.REFRESH.create(), e -> confirmRedraw());
            redraw.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            redraw.setTooltipText(Tr.t("O nouă tragere la sorți, cât timp nu există rezultate"));
            add(redraw);
        }
        if (t.getStage() == TournamentStage.FINALS) {
            add(hint(Tr.t("Rezultatele din grupe au stabilit calificarea și nu mai pot fi modificate.")));
        }
        boolean groupsEditable = details.canRecordResults() && t.getStage() == TournamentStage.GROUPS;
        for (GroupView group : details.groupStage()) {
            add(groupSection(details, group, groupsEditable));
        }
        add(legend(details));
    }

    private static Component stageTitle(TournamentStage stage, List<GroupView> groups) {
        long played = groups.stream().mapToLong(GroupView::playedMatches).sum();
        long total = groups.stream().mapToLong(g -> g.matches().size()).sum();
        H3 title = new H3(stage.getLabel());
        title.getStyle().set("margin-bottom", "0");
        Span progress = new Span(Tr.t("Meciuri jucate: {0} din {1}", played, total));
        progress.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout heading = new HorizontalLayout(title, progress);
        heading.setAlignItems(FlexComponent.Alignment.BASELINE);
        heading.getStyle().set("flex-wrap", "wrap").set("margin-top", "var(--lumo-space-l)");
        return heading;
    }

    /** O grupă: tabelul-matrice și meciurile ei. */
    private Component groupSection(TournamentDetails details, GroupView group, boolean editable) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        H4 name = new H4(TournamentLabels.groupName(group.group().getName()));
        name.getStyle().set("margin", "var(--lumo-space-s) 0 0");
        section.add(name, new ResultsMatrix(group.members(), group.standings(), ownId(details), details.ratingChanges()));
        Component tiebreaks = tiebreakSection(details, group.group().getId(), group.members(), group.tiebreaks(),
                group.pendingTiebreaks(), editable);
        if (tiebreaks != null) {
            section.add(tiebreaks);
        }
        if (!group.carried().isEmpty()) {
            section.add(hint(group.carried().size() == 1
                    ? Tr.t("1 meci e preluat din etapa 1 (jucătorii s-au întâlnit deja în grupă) și nu se mai joacă.")
                    : Tr.t("{0} meciuri sunt preluate din etapa 1 (jucătorii s-au întâlnit deja în grupă) și nu se mai joacă.", group.carried().size())));
        }
        if (!group.matches().isEmpty()) {
            section.add(rounds(details, group.matches(), editable));
        }
        return section;
    }

    // ------------------------------------------------------------------
    // Seturi de departajare
    // ------------------------------------------------------------------

    /**
     * Seturile de departajare ale unui tabel: cele jucate și cele care trebuie jucate.
     * {@code null} dacă tabelul nu are egalități de departajat prin seturi.
     */
    private Component tiebreakSection(TournamentDetails details, Long groupId, List<TournamentParticipant> members,
                                      List<TournamentTiebreak> played, List<StandingsCalculator.Pairing> pending,
                                      boolean editable) {
        if (played.isEmpty() && pending.isEmpty()) {
            return null;
        }
        Map<Long, TournamentParticipant> byId = new HashMap<>();
        members.forEach(p -> byId.put(p.getId(), p));

        Div card = card();
        tiebreakCards.put(groupId != null ? groupId : 0L, card);
        if (!pending.isEmpty()) {
            card.getStyle()
                    .set("border-color", "var(--lumo-warning-color-50pct, var(--lumo-primary-color-50pct))")
                    .set("background", "var(--lumo-warning-color-10pct, var(--lumo-primary-color-10pct))");
        }
        H4 title = new H4(Tr.t("Seturi de departajare"));
        title.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
        card.add(title, hint(Tr.t("Jucătorii rămași la egalitate după meciurile directe joacă între ei câte un set, la 11 puncte. Decid setele câștigate, apoi raportul punctelor; dacă egalitatea rămâne, se joacă o nouă rundă.")));

        for (TournamentTiebreak t : played) {
            card.add(tiebreakRow(details, groupId, t.getRoundNo(), t.getParticipantA(), t.getParticipantB(),
                    t.getPointsA(), t.getPointsB(), editable));
        }
        for (StandingsCalculator.Pairing p : pending) {
            card.add(tiebreakRow(details, groupId, p.round(), byId.get(p.a()), byId.get(p.b()), null, null, editable));
        }
        return card;
    }

    private Component tiebreakRow(TournamentDetails details, Long groupId, int round, TournamentParticipant a,
                                  TournamentParticipant b, Integer pointsA, Integer pointsB, boolean editable) {
        boolean played = pointsA != null;
        Span roundLabel = new Span(Tr.t("Runda {0}", round));
        roundLabel.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("min-width", "4rem");
        Span nameA = matchName(a, played && pointsA > pointsB, true, null);
        Span nameB = matchName(b, played && pointsB > pointsA, false, null);
        Span score = new Span(played ? pointsA + " : " + pointsB : "–:–");
        score.getStyle().set("min-width", "3.5rem").set("text-align", "center")
                .set("font-weight", played ? "700" : "400");
        if (!played) {
            score.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        }
        HorizontalLayout row = new HorizontalLayout(roundLabel, nameA, score, nameB);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
        if (editable) {
            Button enter = new Button(played ? VaadinIcon.EDIT.create() : VaadinIcon.PLUS.create(),
                    e -> new TiebreakDialog(a.getPlayer().getDisplayName(), b.getPlayer().getDisplayName(), round,
                            pointsA, pointsB, (pa, pb) -> {
                        tournamentService.recordTiebreak(tournamentId, groupId, round, a.getId(), b.getId(), pa, pb);
                        Notifications.success(Tr.t("Rezultat salvat"));
                        refresh();
                        scrollTo(tiebreakCards.get(groupId != null ? groupId : 0L));
                    }).open());
            enter.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            enter.setAriaLabel(Tr.t("Set de departajare {0} – {1}", a.getPlayer().getDisplayName(), b.getPlayer().getDisplayName()));
            enter.setTooltipText(played ? Tr.t("Modifică rezultatul") : Tr.t("Introdu rezultatul"));
            row.add(enter);
        }
        return row;
    }

    private Component createStartFinalsCard(TournamentDetails details) {
        Div card = card();
        card.getStyle()
                .set("border-color", "var(--lumo-success-color-50pct)")
                .set("background", "var(--lumo-success-color-10pct)");
        Span text = new Span(Tr.t("Etapa 1 s-a terminat. Alegeți câți jucători din fiecare grupă trec în Finala 1; ceilalți joacă în Finala 2."));
        Button start = new Button(Tr.t("Începe etapa 2"), VaadinIcon.PLAY.create(), e -> new StartFinalsDialog(
                details.groupStage(), qualifiers -> {
                    tournamentService.startFinals(tournamentId, qualifiers);
                    Notifications.success(Tr.t("Etapa 2 a început"));
                    refresh();
                }).open());
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        HorizontalLayout row = new HorizontalLayout(text, start);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.getStyle().set("flex-wrap", "wrap");
        text.getStyle().set("flex", "1");
        card.add(row);
        return card;
    }

    private static Span hint(String text) {
        Span hint = new Span(text);
        hint.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        return hint;
    }

    private Component matchRow(TournamentDetails details, TournamentMatch match, Long own, boolean editable) {
        TournamentParticipant a = match.getParticipantA();
        TournamentParticipant b = match.getParticipantB();
        boolean aWon = match.isPlayed() && match.getWinner().getId().equals(a.getId());
        boolean bWon = match.isPlayed() && match.getWinner().getId().equals(b.getId());

        Span nameA = matchName(a, aWon, true, match.getRatingDeltaA());
        Span nameB = matchName(b, bWon, false, match.getRatingDeltaB());
        Span result;
        if (!match.isPlayed() && details.tournament().getStatus() == TournamentStatus.FINISHED) {
            result = Badges.badge(Tr.t("nejucat"), Badges.Tone.CONTRAST);
            result.getElement().setAttribute("title", Tr.t("Turneul a fost încheiat înainte de acest meci"));
        } else if (!match.isPlayed()) {
            result = new Span("–:–");
            result.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        } else if (match.isWalkover()) {
            result = Badges.badge(aWon ? "W : L" : "L : W", Badges.Tone.ERROR);
            result.getElement().setAttribute("title", Tr.t("Victorie tehnică"));
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
        matchRows.put(match.getId(), row);
        boolean ownMatch = own != null && (own.equals(a.getId()) || own.equals(b.getId()));
        if (ownMatch) {
            row.getStyle().set("background", "var(--lumo-primary-color-10pct)").set("border-radius", "var(--lumo-border-radius-s)");
        }

        if (editable) {
            Button enter = new Button(match.isPlayed() ? VaadinIcon.EDIT.create() : VaadinIcon.PLUS.create(),
                    e -> openResultDialog(details, match));
            enter.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
            String label = match.isPlayed()
                    ? Tr.t("Modifică rezultatul {0} – {1}", a.getPlayer().getDisplayName(), b.getPlayer().getDisplayName())
                    : Tr.t("Introdu rezultatul {0} – {1}", a.getPlayer().getDisplayName(), b.getPlayer().getDisplayName());
            enter.setAriaLabel(label);
            enter.setTooltipText(match.isPlayed() ? Tr.t("Modifică rezultatul") : Tr.t("Introdu rezultatul"));
            row.add(enter);
        }
        return row;
    }

    /** Numele jucătorului, cu schimbarea de rating din meci (la turneele încheiate). */
    private static Span matchName(TournamentParticipant participant, boolean winner, boolean alignRight,
                                  Integer ratingDelta) {
        Span name = new Span(participant.getPlayer().getDisplayName());
        if (ratingDelta != null) {
            Span delta = RatingHistoryList.change(ratingDelta);
            delta.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("margin", "0 0.35em");
            if (alignRight) {
                name.addComponentAsFirst(delta);
            } else {
                name.add(delta);
            }
        }
        name.getStyle()
                .set("flex", "1")
                .set("min-width", "0")
                .set("text-align", alignRight ? "right" : "left")
                .set("font-weight", winner ? "600" : "400");
        return name;
    }

    private void openResultDialog(TournamentDetails details, TournamentMatch match) {
        Runnable onClear = details.manager()
                ? () -> {
                    tournamentService.clearResult(match.getId());
                    Notifications.success(Tr.t("Rezultat șters"));
                    refresh();
                    keepInView(match.getId());
                }
                : null;
        new MatchResultDialog(match, details.tournament().getSetsToWin(), result -> {
            tournamentService.recordResult(match.getId(), result);
            Notifications.success(Tr.t("Rezultat salvat"));
            refresh();
            keepInView(match.getId());
        }, onClear).open();
    }

    /**
     * După reîncărcarea paginii, derulează înapoi la meciul editat (altfel, pe telefon,
     * pagina sare la începutul turneului).
     */
    private void keepInView(Long matchId) {
        scrollTo(matchRows.get(matchId));
    }

    /** Derulează la componenta dată, după reîncărcarea paginii (altfel, pe telefon, pagina sare la început). */
    private static void scrollTo(Component row) {
        if (row != null) {
            row.getElement().executeJs("const el = this; requestAnimationFrame(() => "
                    + "el.scrollIntoView({block: 'center', behavior: 'instant'}));");
        }
    }

    // ------------------------------------------------------------------
    // Administrare
    // ------------------------------------------------------------------

    private void openEditDialog(Tournament tournament) {
        new TournamentFormDialog(tournament, form -> {
            tournamentService.update(tournamentId, form);
            Notifications.success(Tr.t("Turneu actualizat"));
            refresh();
        }).open();
    }

    private void confirmFinish(TournamentDetails details) {
        Tournament tournament = details.tournament();
        long unplayed = details.unplayedMatches();
        StringBuilder text = new StringBuilder(Tr.t("„{0}” trece în starea „Încheiat”, iar ratingul se recalculează din meciurile jucate.", tournament.getName()));
        if (unplayed > 0) {
            text.append(" ").append(unplayed == 1
                    ? Tr.t("1 meci nu are rezultat: rămâne nejucat și nu contează la clasament sau la rating.")
                    : Tr.t("{0} meciuri nu au rezultat: rămân nejucate și nu contează la clasament sau la rating.", unplayed));
        }
        if (tournament.isGroupsFormat() && tournament.getStage() == TournamentStage.GROUPS) {
            text.append(" ").append(Tr.t("Etapa 2 (finalele) nu se mai joacă."));
        }
        text.append(" ").append(Tr.t("Rezultatele pot fi corectate și după încheiere."));
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Încheiați turneul?"));
        dialog.setText(text.toString());
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Încheie turneul"));
        dialog.addConfirmListener(e -> {
            try {
                tournamentService.finishManually(tournamentId);
                Notifications.success(Tr.t("Turneu încheiat"));
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
            refresh();
        });
        dialog.open();
    }

    private void confirmRedraw() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Reîmpărțiți grupele?"));
        dialog.setText(Tr.t("Jucătorii se împart din nou în grupe, prin tragere la sorți pe urne după rating, iar meciurile grupelor se generează din nou. Se poate doar cât timp nu s-a înregistrat niciun rezultat."));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Reîmparte grupele"));
        dialog.addConfirmListener(e -> run(() -> tournamentService.redrawGroups(tournamentId), Tr.t("Grupele au fost reîmpărțite")));
        dialog.open();
    }

    private void confirmDelete(Tournament tournament) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Ștergeți turneul?"));
        dialog.setText(Tr.t("„{0}” va fi șters definitiv, împreună cu înscrierile și toate rezultatele lui.", tournament.getName()));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Șterge"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                tournamentService.delete(tournamentId);
                Notifications.success(Tr.t("Turneu șters"));
                getUI().ifPresent(ui -> ui.navigate(TournamentsView.class));
            } catch (BusinessException ex) {
                Notifications.error(ex.getLocalizedMessage());
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
