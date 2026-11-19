package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import md.ttm.service.player.PlayerPhotoService;
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
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import com.vaadin.flow.component.tabs.Tab;
import com.vaadin.flow.component.tabs.Tabs;
import com.vaadin.flow.component.tabs.TabsVariant;
import md.ttm.common.BusinessException;
import md.ttm.model.rating.RatingHistory;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentGroup;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.model.tournament.TournamentTiebreak;
import md.ttm.service.tournament.StandingsCalculator;
import md.ttm.service.tournament.Bracket;
import md.ttm.service.tournament.KnockoutView;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.model.tournament.TournamentStage;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.security.SecurityUtils;
import md.ttm.service.tournament.GroupView;
import md.ttm.service.tournament.PrizePlace;
import md.ttm.service.tournament.TournamentDetails;
import md.ttm.service.admin.TestToolsService;
import md.ttm.service.tournament.TournamentService;
import md.ttm.ui.account.LoginView;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.RatingHistoryList;

import java.util.List;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
    private final PlayerPhotoService photoService;
    private final TestToolsService testTools;
    private Long tournamentId;
    /** Rezultatele din etapa 2 înainte de o corectură din grupe (pentru mesajul de după salvare); altfel {@code null}. */
    private Long stage2PlayedBefore;
    private String title = "Turneu | TTM";
    /** Etapa aleasă pe taburi ({@code null} = cea în curs) și grupa aleasă ({@code null} = toate). */
    private String selectedStage;
    private String selectedPart;

    public TournamentView(TournamentService tournamentService, PlayerPhotoService photoService,
                          TestToolsService testTools) {
        this.tournamentService = tournamentService;
        this.photoService = photoService;
        this.testTools = testTools;
        setMaxWidth("80rem");
        setWidthFull();
    }

    @Override
    public void setParameter(BeforeEvent event, Long id) {
        this.tournamentId = id;
        bracketMode = BracketMode.GRAPHIC;
        Map<String, List<String>> params = event.getLocation().getQueryParameters().getParameters();
        selectedStage = params.getOrDefault(STAGE_PARAM, List.of()).stream().findFirst().orElse(null);
        selectedPart = params.getOrDefault(PART_PARAM, List.of()).stream().findFirst().orElse(null);
        refresh();
    }

    @Override
    public String getPageTitle() {
        return title;
    }

    /** Rândurile meciurilor, ca după salvarea unui rezultat pagina să rămână la meciul respectiv. */
    private final Map<Long, Component> matchRows = new HashMap<>();
    /** Tabelele-matrice, după id-ul grupei (0 = tabelul Round Robin), ca să revenim la ele după editare. */
    private final Map<Long, Component> matrices = new HashMap<>();
    /** Casetele cu seturile de departajare, după grupă (0 = tabelul turneului Round Robin). */
    private final Map<Long, Component> tiebreakCards = new HashMap<>();

    private void refresh() {
        stageTabs = null;
        matchRows.clear();
        matrices.clear();
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
        if (details.manager() && details.readyToFinish()) {
            add(createFinishCard(details));
        }
        if (details.tournament().isStarted() && details.tournament().isGroupsFormat()) {
            addGroupsTournament(details);
        } else if (details.tournament().isStarted() && details.tournament().isSingleKnockout()) {
            addKnockoutTournament(details);
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
            if (testTools.enabled() && t.getStatus() == TournamentStatus.IN_PROGRESS) {
                Button fill = new Button(Tr.t("Completează rezultatele (test)"), VaadinIcon.MAGIC.create(),
                        e -> confirmFillResults());
                fill.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_CONTRAST);
                fill.setTooltipText(Tr.t("Doar în aplicația de test: rezultate aleatorii pentru toate meciurile fără rezultat"));
                titleRow.add(fill);
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
            Button addPlayers = new Button(Tr.t("Adaugă jucători"), VaadinIcon.PLUS.create(), e ->
                    new AddPlayersDialog(details.tournament().getName(), details.participants().size(),
                            tournamentService.findPlayersNotRegistered(tournamentId), photoService.versions(), ids -> {
                                tournamentService.addParticipants(tournamentId, ids);
                                Notifications.success(ids.size() == 1
                                        ? Tr.t("Jucătorul a fost înscris")
                                        : Tr.t("Au fost înscriși {0} jucători", ids.size()));
                                refresh();
                            }).open());
            addPlayers.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
            actions.add(addPlayers);

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
                .setHeader("#").setWidth("3rem").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        // fără oraș: pe telefon numele trebuie să încapă întreg
        grid.addColumn(p -> p.getPlayer().getDisplayName()).setHeader(Tr.t("Nume Prenume")).setFlexGrow(1);
        grid.addColumn(p -> p.getPlayer().getRating()).setHeader(Tr.t("Rating"))
                .setWidth("5rem").setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
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
            bracketMode = BracketMode.GRAPHIC;
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
        if (canEditTable(details, null)) {
            heading.add(editTableButton(details, null, Tr.t("Editează jucătorii"), Tr.t("Jucătorii turneului"),
                    details.participants(), details.matches()));
        }

        ResultsMatrix matrix = new ResultsMatrix(details.participants(), details.standings(), ownId(details),
                details.ratingChanges(), details.matches(),
                details.canRecordResults() ? m -> openResultDialog(details, m, true) : null);
        matrices.put(0L, matrix);
        section.add(heading, matrix);
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
        String ties = Tr.t("La egalitate de puncte decid meciurile directe; dacă egalitatea rămâne, jucătorii joacă între ei câte un set de departajare.");
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
        List<Component> groupsTop = new ArrayList<>();
        if (details.canRedrawGroups()) {
            Button redraw = new Button(Tr.t("Reîmparte grupele"), VaadinIcon.REFRESH.create(), e -> confirmRedraw());
            redraw.addThemeVariants(ButtonVariant.LUMO_TERTIARY);
            redraw.setTooltipText(Tr.t("O nouă tragere la sorți, cât timp nu există rezultate"));
            groupsTop.add(redraw);
        }
        boolean afterGroups = t.getStage() != TournamentStage.GROUPS;
        if (afterGroups) {
            groupsTop.add(hint(details.manager()
                    ? Tr.t("Rezultatele din grupe au stabilit calificarea. Le puteți corecta, dar etapa 2 se va construi din nou.")
                    : Tr.t("Rezultatele din grupe au stabilit calificarea și nu mai pot fi modificate.")));
        }
        boolean groupsEditable = details.canRecordResults() && (!afterGroups || details.manager());
        List<StageTabs.Part> groupParts = new ArrayList<>();
        for (GroupView group : details.groupStage()) {
            groupParts.add(new StageTabs.Part(shortGroupLabel(group.group().getName()),
                    groupSection(details, group, groupsEditable)));
        }
        StageTabs.Stage groups = new StageTabs.Stage(STAGE_GROUPS, Tr.t("Grupe"),
                progress(details.groupStage().stream().mapToLong(GroupView::playedMatches).sum(),
                        details.groupStage().stream().mapToLong(g -> g.matches().size()).sum()),
                groupsTop, groupParts);

        List<Component> stage2Top = new ArrayList<>();
        List<StageTabs.Part> stage2Parts = new ArrayList<>();
        long played = 0;
        long total = 0;
        List<KnockoutView> brackets = details.knockouts();
        if (!brackets.isEmpty()) {
            stage2Top.add(BracketDiagram.styles());
            stage2Top.add(bracketModeSwitch());
            for (int i = 0; i < brackets.size(); i++) {
                KnockoutView view = brackets.get(i);
                stage2Parts.add(new StageTabs.Part(brackets.size() > 1 ? Tr.t("Tabloul {0}", i + 1) : Tr.t("Tablou"),
                        knockoutSection(details, view, i + 1, brackets.size())));
                played += view.matches().values().stream().filter(TournamentMatch::isPlayed).count();
                total += view.matches().size();
            }
        }
        for (GroupView group : details.finals()) {
            stage2Parts.add(new StageTabs.Part(TournamentLabels.groupName(group.group().getName()),
                    groupSection(details, group, details.canRecordResults())));
            played += group.playedMatches();
            total += group.matches().size();
        }
        if (stage2Parts.isEmpty()) {
            stage2Top.add(hint(Tr.t("Etapa 2 (finale sau tablou eliminatoriu) se alcătuiește după ce se termină grupele; tipul ei se alege atunci.")));
        }
        String stage2Label = t.getStage() == TournamentStage.KNOCKOUT ? Tr.t("Tablou")
                : t.getStage() == TournamentStage.FINALS ? Tr.t("Finale") : Tr.t("Etapa 2");
        StageTabs.Stage stage2 = new StageTabs.Stage(STAGE_2, stage2Label,
                stage2Parts.isEmpty() ? null : progress(played, total), stage2Top, stage2Parts);

        stageTabs = new StageTabs(List.of(groups, stage2), afterGroups ? 1 : 0, selectedStage, selectedPart,
                (stageKey, part) -> {
                    selectedStage = stageKey;
                    selectedPart = part;
                    updateUrl();
                });
        add(stageTabs);
        add(legend(details));
    }

    /** Doar în aplicația de test: rezultate aleatorii pentru meciurile fără rezultat și seturile de departajare. */
    private void confirmFillResults() {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Completează rezultatele?"));
        dialog.setText(Tr.t("Toate meciurile fără rezultat din etapa curentă (și cele care apar pe parcurs în tablou) primesc rezultate aleatorii; jucătorul cu rating mai mare câștigă mai des. Se completează și seturile de departajare. Turneul nu trece singur la etapa următoare și nu se încheie."));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Completează"));
        dialog.addConfirmListener(e -> {
            try {
                int filled = testTools.fillResults(tournamentId);
                Notifications.success(Tr.t("Rezultate completate: {0}", filled));
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
            refresh();
        });
        dialog.open();
    }

    /** Al doilea pas din „Începe etapa 2”: configurarea tipului ales (finalele sau tabloul). */
    private void openStageTwo(TournamentDetails details, KnockoutType knockoutType) {
        if (knockoutType != null) {
            new StartKnockoutDialog(details.groupStage(), knockoutType, (qualifiers, type) -> {
                tournamentService.startKnockout(tournamentId, qualifiers, type);
                Notifications.success(Tr.t("Etapa 2 a început"));
                showStage2();
            }).open();
        } else {
            new StartFinalsDialog(details.groupStage(), details.tournament().isCommercial(), settings -> {
                tournamentService.startFinals(tournamentId, settings);
                Notifications.success(Tr.t("Etapa 2 a început"));
                showStage2();
            }).open();
        }
    }

    /** Turneu de o etapă pe tablou eliminatoriu: tabloul cu toți jucătorii. */
    private void addKnockoutTournament(TournamentDetails details) {
        add(BracketDiagram.styles(), bracketModeSwitch());
        List<KnockoutView> brackets = details.knockouts();
        for (int i = 0; i < brackets.size(); i++) {
            add(knockoutSection(details, brackets.get(i), i + 1, brackets.size()));
        }
    }

    /** Totul s-a jucat: turneul nu se încheie singur, organizatorul confirmă încheierea. */
    private Component createFinishCard(TournamentDetails details) {
        Div card = card();
        card.getStyle()
                .set("border-color", "var(--lumo-primary-color-50pct)")
                .set("background", "var(--lumo-primary-color-10pct)");
        Span text = new Span(Tr.t("Toate meciurile s-au jucat. Încheiați turneul ca să se stabilească clasamentul final și să se actualizeze ratingul."));
        Button finish = new Button(Tr.t("Încheie turneul"), VaadinIcon.FLAG_CHECKERED.create(), e -> confirmFinish(details));
        finish.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        HorizontalLayout row = new HorizontalLayout(text, finish);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.getStyle().set("flex-wrap", "wrap");
        text.getStyle().set("flex", "1");
        card.add(row);
        return card;
    }

    /** Taburile etapelor din pagina curentă ({@code null} la Round Robin sau înainte de începere). */
    private StageTabs stageTabs;

    /** După pornirea etapei 2 pagina trece la finale (tablouri), toate, și derulează la ele. */
    private void showStage2() {
        bracketMode = BracketMode.GRAPHIC;
        selectedStage = STAGE_2;
        selectedPart = StageTabs.ALL;
        refresh();
        updateUrl();
        if (stageTabs != null) {
            stageTabs.getElement().executeJs("this.scrollIntoView({block: 'start', behavior: 'smooth'})");
        }
    }

    private static final String STAGE_GROUPS = "1";
    private static final String STAGE_2 = "2";
    private static final String STAGE_PARAM = "etapa";
    private static final String PART_PARAM = "grupa";

    private static String progress(long played, long total) {
        return total == 0 ? null : played + "/" + total;
    }

    /** „Grupa A” → „A” pe cip; celelalte nume rămân întregi. */
    private static String shortGroupLabel(String stored) {
        return stored != null && stored.startsWith("Grupa ") ? stored.substring("Grupa ".length())
                : TournamentLabels.groupName(stored);
    }

    /** Etapa și grupa alese se păstrează în adresă, ca la reîmprospătare sau într-un link trimis să se deschidă la fel. */
    private void updateUrl() {
        UI ui = UI.getCurrent();
        if (ui == null || tournamentId == null) {
            return;
        }
        Map<String, String> params = new LinkedHashMap<>();
        if (selectedStage != null) {
            params.put(STAGE_PARAM, selectedStage);
        }
        if (selectedPart != null) {
            params.put(PART_PARAM, selectedPart);
        }
        ui.getPage().getHistory().replaceState(null,
                new Location("turneu/" + tournamentId, QueryParameters.simple(params)));
    }

    private Component groupSection(TournamentDetails details, GroupView group, boolean editable) {
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);
        H4 name = new H4(TournamentLabels.groupName(group.group().getName()));
        name.getStyle().set("margin", "var(--lumo-space-s) 0 0");
        if (canEditTable(details, group.group())) {
            HorizontalLayout heading = new HorizontalLayout(name, editTableButton(details, group.group(),
                    Tr.t("Editează grupa"), TournamentLabels.groupName(group.group().getName()), group.members(), group.matches()));
            heading.setAlignItems(FlexComponent.Alignment.BASELINE);
            heading.getStyle().set("flex-wrap", "wrap");
            section.add(heading);
        } else {
            section.add(name);
        }
        ResultsMatrix matrix = new ResultsMatrix(group.members(), group.standings(), ownId(details),
                details.ratingChanges(), group.matches(), editable ? m -> openResultDialog(details, m, true) : null);
        matrices.put(group.group().getId(), matrix);
        section.add(matrix);
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
    // Editarea grupelor după începere
    // ------------------------------------------------------------------

    /** Organizatorul poate adăuga sau scoate jucători: Round Robin în desfășurare, grupele în etapa 1, finalele în etapa 2. */
    private static boolean canEditTable(TournamentDetails details, TournamentGroup group) {
        Tournament t = details.tournament();
        if (!details.manager() || t.getStatus() != md.ttm.model.tournament.TournamentStatus.IN_PROGRESS) {
            return false;
        }
        if (group == null) {
            return t.isRoundRobin();
        }
        return (group.isGroupStage() && t.getStage() == TournamentStage.GROUPS)
                || (group.getStage() == TournamentStage.FINALS && t.getStage() == TournamentStage.FINALS);
    }

    private Button editTableButton(TournamentDetails details, TournamentGroup group, String label, String title,
                                   List<TournamentParticipant> members, List<TournamentMatch> matches) {
        Long groupId = group != null ? group.getId() : null;
        boolean finalTable = group != null && !group.isGroupStage();
        String explanation = finalTable
                ? Tr.t("Jucătorii adăugați (încă neînscriși la turneu) primesc meciuri cu toți cei din finală. Un jucător scos iese doar din finală, iar meciurile lui din finală se șterg.")
                : Tr.t("Jucătorii adăugați primesc meciuri cu toți ceilalți din tabel, în tururile în care sunt liberi. Un jucător scos iese din turneu, iar meciurile lui se șterg.");
        Button edit = new Button(label, VaadinIcon.USERS.create(), e -> new TableEditDialog(title, explanation, members, matches,
                photoService.versions(),
                () -> new AddPlayersDialog(title, members.size(), tournamentService.findPlayersForTable(tournamentId, groupId),
                        photoService.versions(), group != null, ids -> {
                    tournamentService.addPlayersToTable(tournamentId, groupId, ids);
                    Notifications.success(ids.size() == 1 ? Tr.t("Jucătorul a fost adăugat")
                            : Tr.t("Au fost adăugați {0} jucători", ids.size()));
                    refresh();
                }).open(),
                p -> {
                    tournamentService.removePlayerFromTable(tournamentId, groupId, p.getId());
                    Notifications.success(Tr.t("{0} a fost scos", p.getPlayer().getDisplayName()));
                    refresh();
                }).open());
        edit.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
        return edit;
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
            Runnable openTiebreak = () -> new TiebreakDialog(a.getPlayer().getDisplayName(), b.getPlayer().getDisplayName(), round,
                    pointsA, pointsB, (pa, pb) -> {
                tournamentService.recordTiebreak(tournamentId, groupId, round, a.getId(), b.getId(), pa, pb);
                Notifications.success(Tr.t("Rezultat salvat"));
                reportStage2Rebuild();
                refresh();
                scrollTo(tiebreakCards.get(groupId != null ? groupId : 0L));
            }).open();
            boolean rebuild = groupId != null && details.tournament().getStage() != TournamentStage.GROUPS
                    && details.groupStage().stream().anyMatch(g -> g.group().getId().equals(groupId));
            Button enter = new Button(played ? VaadinIcon.EDIT.create() : VaadinIcon.PLUS.create(),
                    e -> {
                        if (rebuild) {
                            confirmStage2Rebuild(details, openTiebreak);
                        } else {
                            stage2PlayedBefore = null;
                            openTiebreak.run();
                        }
                    });
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
        Span text = new Span(Tr.t("Etapa 1 s-a terminat. Alegeți tipul etapei 2 (finale Round Robin sau tablou eliminatoriu) și cine trece unde."));
        Button start = new Button(Tr.t("Începe etapa 2"), VaadinIcon.PLAY.create(),
                e -> new StageTwoDialog(details.tournament(), type -> openStageTwo(details, type)).open());
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        HorizontalLayout row = new HorizontalLayout(text, start);
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.setWidthFull();
        row.getStyle().set("flex-wrap", "wrap");
        text.getStyle().set("flex", "1");
        card.add(row);
        return card;
    }

    // ------------------------------------------------------------------
    // Tabloul eliminatoriu („Grupe + eliminare”)
    // ------------------------------------------------------------------

    private Component knockoutSection(TournamentDetails details, KnockoutView view, int number, int count) {
        Bracket.State state = view.state();
        VerticalLayout section = new VerticalLayout();
        section.setPadding(false);

        H3 title = new H3(details.tournament().isSingleKnockout() ? Tr.t("Tablou eliminatoriu")
                : count > 1 ? Tr.t("Etapa 2 — Tabloul {0}", number) : TournamentStage.KNOCKOUT.getLabel());
        title.getStyle().set("margin-bottom", "0");
        long played = view.matches().values().stream().filter(TournamentMatch::isPlayed).count();
        Span progress = new Span(Tr.t("Meciuri jucate: {0} din {1}", played, view.matches().size()));
        progress.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Span typeBadge = Badges.badge(details.tournament().getKnockoutType().getLabel(), Badges.Tone.CONTRAST);
        typeBadge.getStyle().set("white-space", "normal").set("max-width", "100%");
        HorizontalLayout heading = new HorizontalLayout(title, progress, typeBadge);
        heading.setAlignItems(FlexComponent.Alignment.BASELINE);
        heading.getStyle().set("flex-wrap", "wrap").set("margin-top", "var(--lumo-space-l)");
        section.add(heading);
        if (count > 1) {
            section.add(hint(number == 1
                    ? Tr.t("Jucătorii calificați din grupe joacă pentru locurile 1, 2 și 3.")
                    : Tr.t("Ceilalți jucători din grupe joacă pentru câștigătorul părții a doua.")));
        }

        if (bracketMode() == BracketMode.STANDINGS) {
            section.add(knockoutStandings(details, view));
            return section;
        }

        Long own = ownId(details);
        boolean editable = details.canRecordResults();
        boolean doubleElimination = state.type() == md.ttm.model.tournament.KnockoutType.DOUBLE;
        for (Bracket.Section part : Bracket.Section.values()) {
            Map<Integer, List<Bracket.Match>> rounds = state.matches().stream()
                    .filter(m -> m.section() == part)
                    .filter(m -> visible(m))
                    .collect(Collectors.groupingBy(Bracket.Match::round, TreeMap::new, Collectors.toList()));
            if (rounds.isEmpty()) {
                continue;
            }
            if (doubleElimination && part != Bracket.Section.FINAL) {
                H4 partTitle = new H4(switch (part) {
                    case UPPER -> Tr.t("Tabloul principal");
                    default -> Tr.t("Ramura de jos (pentru locul 3)");
                });
                partTitle.getStyle().set("margin", "var(--lumo-space-s) 0 0");
                section.add(partTitle);
            }
            if (bracketMode() == BracketMode.GRAPHIC) {
                section.add(BracketDiagram.of(view, part, own, editable ? m -> openResultDialog(details, m) : null,
                        m -> roundLabel(m, state.type()), matchRows));
                continue;
            }
            Div grid = new Div();
            grid.getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fill, minmax(min(100%, 22rem), 1fr))")
                    .set("gap", "var(--lumo-space-m)")
                    .set("width", "100%");
            rounds.forEach((round, matches) -> {
                Div roundCard = card();
                H4 roundTitle = new H4(roundLabel(matches.get(0), state.type()));
                roundTitle.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
                roundCard.add(roundTitle);
                for (Bracket.Match m : matches) {
                    TournamentMatch row = view.matches().get(m.key());
                    roundCard.add(row != null ? matchRow(details, row, own, editable) : bracketPlaceholder(view, m));
                }
                grid.add(roundCard);
            });
            section.add(grid);
        }
        return section;
    }

    /** Cum se arată tablourile: desenat, ca listă de meciuri pe tururi sau doar clasamentul. */
    private enum BracketMode { GRAPHIC, LIST, STANDINGS }

    /**
     * Modul tablourilor în pagina deschisă. La deschiderea unui turneu, la începerea lui și a etapei 2 se pornește
     * mereu de la tabloul desenat; alegerea rămâne cât timp lucrați în pagină.
     */
    private BracketMode bracketMode = BracketMode.GRAPHIC;

    private BracketMode bracketMode() {
        return bracketMode;
    }

    private Component bracketModeSwitch() {
        Tab drawn = new Tab(VaadinIcon.SITEMAP.create(), new Span(Tr.t("Tablou grafic")));
        Tab list = new Tab(VaadinIcon.LIST.create(), new Span(Tr.t("Listă de meciuri")));
        Tab standings = new Tab(VaadinIcon.TROPHY.create(), new Span(Tr.t("Clasament")));
        Tabs tabs = new Tabs(drawn, list, standings);
        tabs.addThemeVariants(TabsVariant.LUMO_SMALL);
        tabs.setSelectedTab(switch (bracketMode()) {
            case GRAPHIC -> drawn;
            case LIST -> list;
            case STANDINGS -> standings;
        });
        tabs.addSelectedChangeListener(e -> {
            BracketMode mode = e.getSelectedTab() == list ? BracketMode.LIST
                    : e.getSelectedTab() == standings ? BracketMode.STANDINGS : BracketMode.GRAPHIC;
            bracketMode = mode;
            refresh();
        });
        tabs.getStyle().set("margin-top", "var(--lumo-space-m)");
        return tabs;
    }

    /** Meciurile fără niciun jucător real (două locuri libere) sau cu o trecere directă încă necunoscută nu se arată. */
    private static boolean visible(Bracket.Match m) {
        boolean byeA = m.a() != null && m.a() == Bracket.BYE;
        boolean byeB = m.b() != null && m.b() == Bracket.BYE;
        if (byeA && byeB) {
            return false;
        }
        return !((byeA && m.b() == null) || (byeB && m.a() == null));
    }

    private static String roundLabel(Bracket.Match m, md.ttm.model.tournament.KnockoutType type) {
        return switch (m.section()) {
            case UPPER -> {
                int fromEnd = m.rounds() - m.round();
                if (fromEnd == 0) {
                    yield Tr.t("Finala");
                }
                yield fromEnd == 1 ? Tr.t("Semifinale") : fromEnd == 2 ? Tr.t("Sferturi de finală") : Tr.t("Turul {0}", m.round());
            }
            case LOWER -> m.round() == m.rounds() ? Tr.t("Finala ramurii de jos (locul 3)") : Tr.t("Turul {0}", m.round());
            case FINAL -> Tr.t("Meci pentru locul 3");
        };
    }

    /** Un meci din tablou care încă nu se joacă: jucătorii știuți, „?” pentru cei încă necunoscuți, sau trecerea directă. */
    private static Component bracketPlaceholder(KnockoutView view, Bracket.Match m) {
        Span nameA = placeholderName(view, m.a(), true);
        Span nameB = placeholderName(view, m.b(), false);
        Span middle;
        if ((m.a() != null && m.a() == Bracket.BYE) || (m.b() != null && m.b() == Bracket.BYE)) {
            middle = Badges.badge(Tr.t("trece direct"), Badges.Tone.CONTRAST);
        } else {
            middle = new Span("–:–");
            middle.getStyle().set("color", "var(--lumo-tertiary-text-color)");
        }
        middle.getStyle().set("min-width", "3.5rem").set("text-align", "center");
        HorizontalLayout row = new HorizontalLayout(nameA, middle, nameB);
        row.setWidthFull();
        row.setAlignItems(FlexComponent.Alignment.CENTER);
        row.getStyle()
                .set("padding", "var(--lumo-space-xs) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)")
                .set("color", "var(--lumo-secondary-text-color)");
        return row;
    }

    private static Span placeholderName(KnockoutView view, Long id, boolean alignRight) {
        String text;
        if (id == null) {
            text = "?";
        } else if (id == Bracket.BYE) {
            text = "—";
        } else {
            TournamentParticipant p = view.participant(id);
            text = p != null ? p.getPlayer().getDisplayName() : "?";
        }
        Span name = new Span(text);
        name.getStyle().set("flex", "1").set("min-width", "0").set("text-align", alignRight ? "right" : "left");
        return name;
    }

    /** Locurile stabilite în tabloul eliminatoriu (toate, după încheiere; până atunci, ale celor eliminați). */
    /** Un rând din clasamentul tabloului: locul ({@code null} cât timp nu e decis) și jucătorul. */
    private record KnockoutRow(Integer place, TournamentParticipant participant) {
    }

    /**
     * Clasamentul tabloului: toți jucătorii lui, cu ratingul și locul ocupat. Locurile decise vin primele; cât timp
     * locul unui jucător nu e decis, coloana rămâne goală, iar jucătorii apar în ordinea din tablou.
     */
    private static Component knockoutStandings(TournamentDetails details, KnockoutView view) {
        Div card = card();
        H4 title = new H4(view.state().complete() ? Tr.t("Clasamentul final") : Tr.t("Clasament"));
        title.getStyle().set("margin", "0 0 var(--lumo-space-xs)");
        card.add(title);

        Map<Long, Integer> placeOf = new HashMap<>();
        view.state().placings().forEach(p -> placeOf.put(p.participantId(), p.place()));
        List<KnockoutRow> rows = new ArrayList<>();
        for (int i = 0; i < view.members().size(); i++) {
            TournamentParticipant p = view.members().get(i);
            rows.add(new KnockoutRow(placeOf.get(p.getId()), p));
        }
        // ordinea din tablou se păstrează la locurile încă nedecise (sortarea e stabilă)
        rows.sort(Comparator.comparing(KnockoutRow::place, Comparator.nullsLast(Comparator.naturalOrder())));

        Grid<KnockoutRow> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
        grid.setAllRowsVisible(true);
        grid.addColumn(r -> r.place() == null ? "" : String.valueOf(r.place()))
                .setHeader(Tr.t("Loc")).setWidth("4.5rem").setFlexGrow(0);
        grid.addColumn(r -> r.participant().getPlayer().getDisplayName())
                .setHeader(Tr.t("Jucător")).setFlexGrow(1);
        grid.addColumn(r -> {
                    RatingHistory change = details.ratingChanges().get(r.participant().getPlayer().getId());
                    if (change == null) {
                        return String.valueOf(r.participant().getSeedRating());
                    }
                    int d = change.getChange();
                    return change.getRatingBefore() + " → " + change.getRatingAfter() + " (" + (d > 0 ? "+" + d : d) + ")";
                })
                .setHeader(Tr.t("Rating")).setAutoWidth(true).setFlexGrow(0);
        grid.setItems(rows);
        card.add(grid);
        if (placeOf.isEmpty()) {
            card.add(hint(Tr.t("Locurile se completează pe măsură ce se decid în tablou.")));
        }
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
        openResultDialog(details, match, false);
    }

    /** @param fromMatrix deschis din tabelul-matrice: după salvare revenim la tabel, nu la rândul meciului */
    private void openResultDialog(TournamentDetails details, TournamentMatch match, boolean fromMatrix) {
        if (rebuildsStage2(details, match.getGroup())) {
            confirmStage2Rebuild(details, () -> showResultDialog(details, match, fromMatrix));
        } else {
            stage2PlayedBefore = null;
            showResultDialog(details, match, fromMatrix);
        }
    }

    /** Un rezultat din grupe după începerea etapei 2: corectarea lui construiește etapa 2 din nou. */
    private static boolean rebuildsStage2(TournamentDetails details, TournamentGroup group) {
        return group != null && group.isGroupStage() && details.tournament().getStage() != TournamentStage.GROUPS;
    }

    /** Mesajul de informare înainte de corectarea unui rezultat din etapa 1, după începerea etapei 2. */
    private void confirmStage2Rebuild(TournamentDetails details, Runnable proceed) {
        long played = stage2Played(details);
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Etapa 2 va fi construită din nou"));
        dialog.setText(Tr.t("Corectarea unui rezultat din grupe poate schimba calificarea. După salvare, etapa 2 se construiește din nou cu aceleași setări. Din cele {0} rezultate înregistrate în etapa 2 se păstrează cele ale meciurilor care rămân aceleași (aceiași jucători în aceeași finală, respectiv pe aceeași poziție în tablou); celelalte se șterg. Dacă etapa 1 nu mai e completă, turneul revine la etapa grupelor.", played));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Continuă"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            stage2PlayedBefore = played;
            proceed.run();
        });
        dialog.open();
    }

    private static long stage2Played(TournamentDetails details) {
        return details.matches().stream()
                .filter(m -> m.getGroup() != null && !m.getGroup().isGroupStage() && m.isPlayed())
                .count();
    }

    /** După o corectură din grupe: câte rezultate din etapa 2 s-au păstrat și câte s-au șters. */
    private void reportStage2Rebuild() {
        if (stage2PlayedBefore == null) {
            return;
        }
        long before = stage2PlayedBefore;
        stage2PlayedBefore = null;
        tournamentService.findDetails(tournamentId).ifPresent(after -> {
            long kept = after.tournament().getStage() == TournamentStage.GROUPS ? 0 : Math.min(before, stage2Played(after));
            Notifications.success(after.tournament().getStage() == TournamentStage.GROUPS
                    ? Tr.t("Etapa 1 nu mai e completă: turneul a revenit la etapa grupelor")
                    : Tr.t("Etapa 2 a fost construită din nou: {0} rezultate păstrate, {1} șterse", kept, before - kept));
        });
    }

    private void showResultDialog(TournamentDetails details, TournamentMatch match, boolean fromMatrix) {
        Runnable onClear = details.manager()
                ? () -> {
                    tournamentService.clearResult(match.getId());
                    Notifications.success(Tr.t("Rezultat șters"));
                    reportStage2Rebuild();
                    refresh();
                    keepInView(match, fromMatrix);
                }
                : null;
        new MatchResultDialog(match, details.tournament().getSetsToWin(), result -> {
            tournamentService.recordResult(match.getId(), result);
            Notifications.success(Tr.t("Rezultat salvat"));
            reportStage2Rebuild();
            refresh();
            keepInView(match, fromMatrix);
        }, onClear).open();
    }

    /**
     * După reîncărcarea paginii, derulează înapoi la meciul editat (altfel, pe telefon,
     * pagina sare la începutul turneului).
     */
    private void keepInView(TournamentMatch match, boolean fromMatrix) {
        scrollTo(fromMatrix
                ? matrices.get(match.getGroup() != null ? match.getGroup().getId() : 0L)
                : matchRows.get(match.getId()));
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
