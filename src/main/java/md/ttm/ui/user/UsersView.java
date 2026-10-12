package md.ttm.ui.user;

import md.ttm.i18n.Tr;
import md.ttm.common.BusinessException;
import md.ttm.model.user.AppUser;
import md.ttm.service.user.UserService;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.Responsive;
import md.ttm.ui.layout.MainLayout;
import md.ttm.ui.player.PlayerView;
import com.vaadin.flow.router.RouterLink;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.confirmdialog.ConfirmDialog;
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
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Administrarea utilizatorilor (jucătorilor cu cont): listă completă, adăugare, editare, ștergere.
 */
@Route(value = "utilizatori", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class UsersView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Utilizatori") + " | TTM";
    }


    private static final ZoneId ZONE = ZoneId.of("Europe/Chisinau");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final UserService userService;
    private final Grid<AppUser> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span counter = new Span();
    private final Span tapHint = new Span(Tr.t("Atingeți un cont pentru a-l edita sau șterge."));
    private List<AppUser> users = List.of();
    private boolean narrowScreen;

    public UsersView(UserService userService) {
        this.userService = userService;
        setSizeFull();
        tapHint.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        tapHint.setVisible(false);
        add(createHeader(), createToolbar(), tapHint, grid);
        configureGrid();
        refresh();
    }

    private Component createHeader() {
        H2 title = new H2(Tr.t("Utilizatori"));
        title.getStyle().set("margin", "0");
        counter.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        return header;
    }

    private Component createToolbar() {
        search.setPlaceholder(Tr.t("Caută după utilizator sau nume"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> applyFilter());
        search.setWidth("min(100%, 22rem)");

        Button add = new Button(Tr.t("Adaugă utilizator"), VaadinIcon.PLUS.create(), e -> openEditor(null));
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);

        HorizontalLayout toolbar = new HorizontalLayout(search, add);
        toolbar.setWidthFull();
        toolbar.setAlignItems(FlexComponent.Alignment.CENTER);
        toolbar.getStyle().set("flex-wrap", "wrap");
        return toolbar;
    }

    private void configureGrid() {
        grid.setSizeFull();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);

        Grid.Column<AppUser> usernameColumn = grid.addColumn(AppUser::getUsername)
                .setHeader(Tr.t("Utilizator"))
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getUsername));
        Grid.Column<AppUser> nameColumn = grid.addComponentColumn(UsersView::playerLink)
                .setHeader(Tr.t("Nume Prenume"))
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        // Pe telefon: o singură coloană cu utilizatorul, numele și rolul
        Grid.Column<AppUser> compactColumn = grid.addComponentColumn(UsersView::compactUserCell)
                .setHeader(Tr.t("Cont"))
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getUsername));
        compactColumn.setVisible(false);
        Grid.Column<AppUser> roleColumn = grid.addColumn(u -> u.getRole().getLabel())
                .setHeader(Tr.t("Rol"))
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getRole));
        grid.addComponentColumn(UsersView::statusBadge)
                .setHeader(Tr.t("Stare"))
                .setAutoWidth(true).setFlexGrow(0);
        Grid.Column<AppUser> createdColumn = grid.addColumn(
                        u -> u.getCreatedAt() == null ? "" : DATE_TIME.format(u.getCreatedAt().atZone(ZONE)))
                .setHeader(Tr.t("Creat la"))
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        Grid.Column<AppUser> actionsColumn = grid.addComponentColumn(this::createActions)
                .setHeader(Tr.t("Acțiuni"))
                .setAutoWidth(true).setFlexGrow(0)
                .setFrozenToEnd(true);

        // Pe telefon se editează atingând rândul (butoanele nu încap)
        grid.addItemClickListener(e -> {
            if (narrowScreen) {
                openEditor(e.getItem());
            }
        });

        Responsive.onNarrowChange(this, narrow -> {
            narrowScreen = narrow;
            tapHint.setVisible(narrow);
            usernameColumn.setVisible(!narrow);
            nameColumn.setVisible(!narrow);
            compactColumn.setVisible(narrow);
            actionsColumn.setVisible(!narrow);
            roleColumn.setVisible(!narrow);
            createdColumn.setVisible(!narrow);
            grid.recalculateColumnWidths();
        });
    }

    private Component createActions(AppUser user) {
        Button edit = new Button(VaadinIcon.EDIT.create(), e -> openEditor(user));
        edit.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY);
        edit.setAriaLabel(Tr.t("Editează {0}", user.getUsername()));
        edit.setTooltipText(Tr.t("Editează"));

        Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(user));
        delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel(Tr.t("Șterge {0}", user.getUsername()));
        delete.setTooltipText(Tr.t("Șterge"));

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.setSpacing(false);
        return actions;
    }

    private void openEditor(AppUser user) {
        new UserFormDialog(user, form -> {
            if (user == null) {
                userService.create(form);
                Notifications.success(Tr.t("Utilizator creat"));
            } else {
                userService.update(user.getId(), form);
                Notifications.success(Tr.t("Modificări salvate"));
            }
            refresh();
        }, user == null ? null : () -> confirmDelete(user)).open();
    }

    private void confirmDelete(AppUser user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader(Tr.t("Ștergeți utilizatorul?"));
        dialog.setText(user.getPlayer() != null
                ? Tr.t("Contul „{0}” va fi șters definitiv, împreună cu profilul de jucător.", user.getUsername())
                : Tr.t("Contul „{0}” va fi șters definitiv.", user.getUsername()));
        dialog.setCancelable(true);
        dialog.setCancelText(Tr.t("Anulează"));
        dialog.setConfirmText(Tr.t("Șterge"));
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.delete(user.getId());
                Notifications.success(Tr.t("Utilizator șters"));
            } catch (BusinessException ex) {
                Notifications.error(ex.getLocalizedMessage());
            }
            refresh();
        });
        dialog.open();
    }

    private void refresh() {
        users = userService.findAll();
        applyFilter();
    }

    private void applyFilter() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<AppUser> filtered = term.isEmpty()
                ? users
                : users.stream()
                    .filter(u -> u.getUsername().contains(term)
                            || u.getDisplayName().toLowerCase(Locale.ROOT).contains(term))
                    .toList();
        grid.setItems(filtered);
        counter.setText(filtered.size() == users.size()
                ? Tr.t("{0} conturi", users.size())
                : Tr.t("{0} din {1} conturi", filtered.size(), users.size()));
    }

    private static Component compactUserCell(AppUser user) {
        Span username = new Span(user.getUsername());
        username.getStyle().set("font-weight", "500");
        Span details = new Span();
        if (user.getPlayer() != null) {
            details.add(playerLink(user), new Span(" · " + user.getRole().getLabel()));
        } else {
            details.setText(Tr.t("fără profil de jucător") + " · " + user.getRole().getLabel());
        }
        details.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout cell = new VerticalLayout(username, details);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.getStyle().set("line-height", "1.3").set("white-space", "normal");
        return cell;
    }

    /**
     * Numele jucătorului, ca legătură spre pagina lui. Apăsarea legăturii nu deschide și editarea contului
     * (pe telefon rândul întreg deschide editarea).
     */
    private static Component playerLink(AppUser user) {
        if (user.getPlayer() == null) {
            return new Span(Tr.t("— (fără profil de jucător)"));
        }
        RouterLink link = new RouterLink(user.getPlayer().getDisplayName(), PlayerView.class, user.getPlayer().getId());
        link.getElement().addEventListener("click", e -> { }).addEventData("event.stopPropagation()");
        return link;
    }

    private static Component statusBadge(AppUser user) {
        return user.isEnabled()
                ? Badges.badge(Tr.t("Activ"), Badges.Tone.SUCCESS)
                : Badges.badge(Tr.t("Blocat"), Badges.Tone.ERROR);
    }
}
