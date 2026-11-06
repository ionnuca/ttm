package md.ttm.ui.tournament;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import md.ttm.i18n.Tr;
import md.ttm.model.player.Player;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.PlayerAvatar;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

/**
 * „Adaugă jucători”: lista jucătorilor neînscriși, după rating, cu căutare și bife; bara de jos arată câți sunt
 * selectați și câți participanți va avea turneul, cu butonul care îi înscrie pe toți deodată.
 */
class AddPlayersDialog extends Dialog {

    private final List<Player> players;
    private final Map<Long, Long> photoVersions;
    private final int registered;
    private final Set<Long> selected = new LinkedHashSet<>();
    private final Map<Long, Row> rows = new LinkedHashMap<>();
    private final Div list = new Div();
    private final Span available = new Span();
    private final Button selectAll = new Button();
    private final Span selectedText = new Span();
    private final Span totalText = new Span();
    private final Button add = new Button(Tr.t("Adaugă"), VaadinIcon.PLUS.create());
    private final Span empty = new Span(Tr.t("Niciun jucător găsit"));
    private List<Player> visible = List.of();

    AddPlayersDialog(String tournamentName, int registered, List<Player> notRegistered, Map<Long, Long> photoVersions,
                     Consumer<List<Long>> onAdd) {
        this.registered = registered;
        this.photoVersions = photoVersions;
        this.players = new ArrayList<>(notRegistered);
        this.players.sort(Comparator.comparingInt(Player::getRating).reversed()
                .thenComparing(Player::getDisplayName, String.CASE_INSENSITIVE_ORDER));

        setHeaderTitle(Tr.t("Adaugă jucători"));
        setWidth("min(96vw, 32rem)");
        setHeight("min(90vh, 52rem)");
        setDraggable(false);
        Button close = new Button(VaadinIcon.CLOSE.create(), e -> close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        close.setAriaLabel(Tr.t("Închide"));
        getHeader().add(close);

        Span subtitle = new Span(Tr.t("{0} · {1} înscriși", tournamentName, registered));
        subtitle.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");

        TextField search = new TextField();
        search.setPlaceholder(Tr.t("Caută după nume sau oraș"));
        search.setAriaLabel(Tr.t("Caută jucători"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.EAGER);
        search.setWidthFull();
        search.addValueChangeListener(e -> filter(e.getValue()));

        available.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        selectAll.addThemeVariants(ButtonVariant.LUMO_TERTIARY_INLINE);
        selectAll.addClickListener(e -> toggleAllVisible());
        HorizontalLayout tools = new HorizontalLayout(available, selectAll);
        tools.setWidthFull();
        tools.setJustifyContentMode(FlexComponent.JustifyContentMode.BETWEEN);
        tools.setAlignItems(FlexComponent.Alignment.CENTER);

        list.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "2px")
                .set("flex", "1").set("overflow-y", "auto").set("min-height", "0").set("margin", "0 calc(var(--lumo-space-s) * -1)");
        empty.getStyle().set("color", "var(--lumo-secondary-text-color)").set("padding", "var(--lumo-space-m)");
        for (Player p : players) {
            rows.put(p.getId(), new Row(p));
        }

        Div content = new Div(subtitle, search, tools, list);
        content.getStyle().set("display", "flex").set("flex-direction", "column").set("gap", "var(--lumo-space-s)")
                .set("height", "100%").set("min-height", "0");
        add(content);

        // bara de jos: câți sunt selectați și câți participanți va avea turneul
        selectedText.getStyle().set("font-weight", "600");
        totalText.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
        Div summary = new Div(selectedText, totalText);
        summary.getStyle().set("display", "flex").set("flex-direction", "column").set("flex", "1").set("min-width", "0");
        add.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        add.addClickListener(e -> {
            try {
                onAdd.accept(List.copyOf(selected));
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        HorizontalLayout bar = new HorizontalLayout(summary, add);
        bar.setWidthFull();
        bar.setAlignItems(FlexComponent.Alignment.CENTER);
        getFooter().add(bar);

        filter("");
        updateSummary();
    }

    private void filter(String query) {
        String q = normalize(query);
        visible = players.stream()
                .filter(p -> q.isEmpty() || normalize(p.getDisplayName()).contains(q)
                        || (p.getCity() != null && normalize(p.getCity()).contains(q)))
                .toList();
        list.removeAll();
        visible.forEach(p -> list.add(rows.get(p.getId())));
        if (visible.isEmpty()) {
            list.add(empty);
        }
        available.setText(q.isEmpty()
                ? Tr.t("{0} jucători neînscriși · după rating", players.size())
                : Tr.t("{0} din {1} jucători", visible.size(), players.size()));
        updateSelectAll();
    }

    private void toggleAllVisible() {
        boolean all = !visible.isEmpty() && visible.stream().allMatch(p -> selected.contains(p.getId()));
        visible.forEach(p -> rows.get(p.getId()).setSelected(!all));
        updateSummary();
    }

    private void updateSelectAll() {
        boolean all = !visible.isEmpty() && visible.stream().allMatch(p -> selected.contains(p.getId()));
        selectAll.setText(all ? Tr.t("Deselectează tot") : Tr.t("Selectează tot"));
        selectAll.setEnabled(!visible.isEmpty());
    }

    private void updateSummary() {
        int n = selected.size();
        selectedText.setText(n == 0 ? Tr.t("Niciun jucător selectat")
                : n == 1 ? Tr.t("1 jucător selectat") : Tr.t("{0} jucători selectați", n));
        totalText.setText(Tr.t("Turneul va avea {0} participanți", registered + n));
        add.setEnabled(n > 0);
        add.setText(n > 1 ? Tr.t("Adaugă {0} jucători", n) : Tr.t("Adaugă"));
        updateSelectAll();
    }

    private static String normalize(String s) {
        if (s == null) {
            return "";
        }
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT).trim();
    }

    /** Un rând din listă: bifa, avatarul, numele și orașul, ratingul; apăsarea oriunde pe rând schimbă bifa. */
    private final class Row extends Div {

        private final Long id;
        private final Checkbox check = new Checkbox();

        Row(Player player) {
            this.id = player.getId();
            addClassName("ttm-add-player");
            check.setAriaLabel(player.getDisplayName());
            check.addValueChangeListener(e -> {
                if (e.isFromClient()) {
                    apply(e.getValue());
                    updateSummary();
                }
            });
            // un clic pe bifă nu ajunge și la rând (altfel bifa s-ar schimba de două ori)
            check.getElement().addEventListener("click", e -> { }).addEventData("event.stopPropagation()");

            Span name = new Span(player.getDisplayName());
            name.getStyle().set("font-weight", "500").set("white-space", "nowrap").set("overflow", "hidden")
                    .set("text-overflow", "ellipsis");
            Span city = new Span(player.getCity() == null || player.getCity().isBlank() ? "—" : player.getCity());
            city.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
            Div text = new Div(name, city);
            text.getStyle().set("display", "flex").set("flex-direction", "column").set("flex", "1").set("min-width", "0");
            Span rating = new Span(String.valueOf(player.getRating()));
            rating.getStyle().set("font-weight", "600").set("font-variant-numeric", "tabular-nums");

            Component avatar = PlayerAvatar.of(player, photoVersions.get(player.getId()), "2.25rem");
            add(check, avatar, text, rating);
            getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-m)")
                    .set("padding", "var(--lumo-space-xs) var(--lumo-space-s)").set("min-height", "3.5rem")
                    .set("box-sizing", "border-box").set("border-radius", "var(--lumo-border-radius-l)")
                    .set("cursor", "pointer").set("flex-shrink", "0");
            addClickListener(e -> {
                setSelected(!selected.contains(id));
                updateSummary();
            });
        }

        void setSelected(boolean value) {
            check.setValue(value);
            apply(value);
        }

        private void apply(boolean value) {
            if (value) {
                selected.add(id);
            } else {
                selected.remove(id);
            }
            getStyle().set("background", value ? "var(--lumo-primary-color-10pct)" : "transparent");
        }
    }
}
