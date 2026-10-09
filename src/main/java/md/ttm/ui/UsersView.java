package md.ttm.ui;

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
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import jakarta.annotation.security.RolesAllowed;
import md.ttm.common.BusinessException;
import md.ttm.user.AppUser;
import md.ttm.user.UserService;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Administrarea utilizatorilor (jucătorilor cu cont): listă completă, adăugare, editare, ștergere.
 */
@Route(value = "utilizatori", layout = MainLayout.class)
@PageTitle("Utilizatori | TTM")
@RolesAllowed("ADMIN")
public class UsersView extends VerticalLayout {

    static final ZoneId ZONE = ZoneId.of("Europe/Chisinau");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final UserService userService;
    private final Grid<AppUser> grid = new Grid<>();
    private final TextField search = new TextField();
    private final Span counter = new Span();
    private final Span tapHint = new Span("Atingeți un cont pentru a-l edita sau șterge.");
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
        H2 title = new H2("Utilizatori");
        title.getStyle().set("margin", "0");
        counter.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);
        return header;
    }

    private Component createToolbar() {
        search.setPlaceholder("Caută după utilizator sau nume");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.addValueChangeListener(e -> applyFilter());
        search.setWidth("min(100%, 22rem)");

        Button add = new Button("Adaugă utilizator", VaadinIcon.PLUS.create(), e -> openEditor(null));
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
                .setHeader("Utilizator")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getUsername));
        Grid.Column<AppUser> nameColumn = grid.addColumn(
                        u -> u.getPlayer() != null ? u.getPlayer().getDisplayName() : "— (fără profil de jucător)")
                .setHeader("Nume Prenume")
                .setFlexGrow(2)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getDisplayName, String.CASE_INSENSITIVE_ORDER));
        // Pe telefon: o singură coloană cu utilizatorul, numele și rolul
        Grid.Column<AppUser> compactColumn = grid.addComponentColumn(UsersView::compactUserCell)
                .setHeader("Cont")
                .setFlexGrow(1)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getUsername));
        compactColumn.setVisible(false);
        Grid.Column<AppUser> roleColumn = grid.addColumn(u -> u.getRole().getLabel())
                .setHeader("Rol")
                .setAutoWidth(true)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getRole));
        grid.addComponentColumn(UsersView::statusBadge)
                .setHeader("Stare")
                .setAutoWidth(true).setFlexGrow(0);
        Grid.Column<AppUser> createdColumn = grid.addColumn(
                        u -> u.getCreatedAt() == null ? "" : DATE_TIME.format(u.getCreatedAt().atZone(ZONE)))
                .setHeader("Creat la")
                .setAutoWidth(true).setFlexGrow(0)
                .setSortable(true)
                .setComparator(Comparator.comparing(AppUser::getCreatedAt,
                        Comparator.nullsLast(Comparator.naturalOrder())));
        Grid.Column<AppUser> actionsColumn = grid.addComponentColumn(this::createActions)
                .setHeader("Acțiuni")
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
        edit.setAriaLabel("Editează " + user.getUsername());
        edit.setTooltipText("Editează");

        Button delete = new Button(VaadinIcon.TRASH.create(), e -> confirmDelete(user));
        delete.addThemeVariants(ButtonVariant.LUMO_ICON, ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.setAriaLabel("Șterge " + user.getUsername());
        delete.setTooltipText("Șterge");

        HorizontalLayout actions = new HorizontalLayout(edit, delete);
        actions.setSpacing(false);
        return actions;
    }

    private void openEditor(AppUser user) {
        new UserFormDialog(user, form -> {
            if (user == null) {
                userService.create(form);
                Notifications.success("Utilizator creat");
            } else {
                userService.update(user.getId(), form);
                Notifications.success("Modificări salvate");
            }
            refresh();
        }, user == null ? null : () -> confirmDelete(user)).open();
    }

    private void confirmDelete(AppUser user) {
        ConfirmDialog dialog = new ConfirmDialog();
        dialog.setHeader("Ștergeți utilizatorul?");
        dialog.setText("Contul „" + user.getUsername() + "” va fi șters definitiv"
                + (user.getPlayer() != null ? ", împreună cu profilul de jucător." : "."));
        dialog.setCancelable(true);
        dialog.setCancelText("Anulează");
        dialog.setConfirmText("Șterge");
        dialog.setConfirmButtonTheme("error primary");
        dialog.addConfirmListener(e -> {
            try {
                userService.delete(user.getId());
                Notifications.success("Utilizator șters");
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
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
                ? users.size() + " conturi"
                : filtered.size() + " din " + users.size() + " conturi");
    }

    private static Component compactUserCell(AppUser user) {
        Span username = new Span(user.getUsername());
        username.getStyle().set("font-weight", "500");
        Span details = new Span((user.getPlayer() != null ? user.getPlayer().getDisplayName() : "fără profil de jucător")
                + " · " + user.getRole().getLabel());
        details.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout cell = new VerticalLayout(username, details);
        cell.setPadding(false);
        cell.setSpacing(false);
        cell.getStyle().set("line-height", "1.3").set("white-space", "normal");
        return cell;
    }

    private static Component statusBadge(AppUser user) {
        Span badge = new Span(user.isEnabled() ? "Activ" : "Blocat");
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "0 var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("line-height", "1.6")
                .set("color", user.isEnabled() ? "var(--lumo-success-text-color)" : "var(--lumo-error-text-color)")
                .set("background", user.isEnabled() ? "var(--lumo-success-color-10pct)" : "var(--lumo-error-color-10pct)");
        return badge;
    }
}
