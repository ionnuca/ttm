package md.ttm.ui.components;

import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.router.BeforeEnterEvent;
import com.vaadin.flow.router.Location;
import com.vaadin.flow.router.QueryParameters;
import md.ttm.i18n.Tr;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntConsumer;

/**
 * Paginarea unei liste: „‹ 1 … 4 5 6 … 12 ›” și „26–50 din 287”. Pagina curentă se păstrează în adresă
 * ({@code ?pagina=3}), ca la revenirea din pagina unui turneu sau jucător lista să fie tot acolo.
 */
public class Pager extends Div {

    /** Parametrul din adresă. */
    public static final String PARAM = "pagina";

    private final int pageSize;
    private final Div pages = new Div();
    private final Span info = new Span();
    private final Button previous = new Button(VaadinIcon.ANGLE_LEFT.create());
    private final Button next = new Button(VaadinIcon.ANGLE_RIGHT.create());
    private final List<IntConsumer> listeners = new ArrayList<>();
    private int total;
    private int page;
    private String path;

    public Pager(int pageSize) {
        this.pageSize = pageSize;
        addClassName("ttm-pager");
        previous.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        previous.setAriaLabel(Tr.t("Pagina anterioară"));
        previous.addClickListener(e -> go(page - 1));
        next.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        next.setAriaLabel(Tr.t("Pagina următoare"));
        next.addClickListener(e -> go(page + 1));
        pages.getStyle().set("display", "flex").set("align-items", "center").set("gap", "2px");
        Div controls = new Div(previous, pages, next);
        controls.getStyle().set("display", "flex").set("align-items", "center").set("gap", "2px");
        info.getStyle().set("color", "var(--lumo-secondary-text-color)").set("font-size", "var(--lumo-font-size-s)");
        add(controls, info);
        getStyle().set("display", "flex").set("flex-wrap", "wrap").set("align-items", "center")
                .set("justify-content", "space-between").set("gap", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("width", "100%").set("padding", "var(--lumo-space-s) 0");
        render();
    }

    public int getPageSize() {
        return pageSize;
    }

    /** Pagina curentă, de la 0. */
    public int getPage() {
        return page;
    }

    public int getPageCount() {
        return Math.max(1, (total + pageSize - 1) / pageSize);
    }

    /** Numărul total de elemente; pagina curentă rămâne, dacă mai există. */
    public void setTotal(int total) {
        this.total = total;
        page = Math.min(page, getPageCount() - 1);
        render();
    }

    /** Fără notificarea ascultătorilor (de ex. la o căutare nouă: înapoi la prima pagină). */
    public void setPage(int page) {
        this.page = Math.max(0, Math.min(page, getPageCount() - 1));
        render();
        updateUrl();
    }

    public int from() {
        return Math.min(page * pageSize, total);
    }

    public int to() {
        return Math.min(from() + pageSize, total);
    }

    /** Elementele paginii curente. */
    public <T> List<T> slice(List<T> items) {
        return items.subList(Math.min(page * pageSize, items.size()), Math.min(page * pageSize + pageSize, items.size()));
    }

    public void addPageChangeListener(IntConsumer listener) {
        listeners.add(listener);
    }

    /**
     * Citește pagina din adresă ({@code ?pagina=N}) și o ține la zi la fiecare schimbare.
     * Se apelează din {@code beforeEnter}, înainte de prima afișare.
     */
    public void bindToUrl(BeforeEnterEvent event) {
        path = event.getLocation().getPath();
        List<String> values = event.getLocation().getQueryParameters().getParameters().getOrDefault(PARAM, List.of());
        int requested = 1;
        if (!values.isEmpty()) {
            try {
                requested = Integer.parseInt(values.get(0));
            } catch (NumberFormatException ignored) {
                requested = 1;
            }
        }
        this.page = Math.max(0, requested - 1);
    }

    private void go(int target) {
        int clamped = Math.max(0, Math.min(target, getPageCount() - 1));
        if (clamped == page) {
            return;
        }
        page = clamped;
        render();
        updateUrl();
        listeners.forEach(l -> l.accept(page));
    }

    private void updateUrl() {
        if (path == null) {
            return;
        }
        UI ui = UI.getCurrent();
        if (ui != null) {
            QueryParameters params = page == 0 ? QueryParameters.empty()
                    : QueryParameters.simple(Map.of(PARAM, String.valueOf(page + 1)));
            ui.getPage().getHistory().replaceState(null, new Location(path, params));
        }
    }

    private void render() {
        int count = getPageCount();
        setVisible(total > pageSize);
        previous.setEnabled(page > 0);
        next.setEnabled(page < count - 1);
        info.setText(Tr.t("{0}–{1} din {2}", from() + 1, to(), total));
        pages.removeAll();
        int last = -1;
        for (int i = 0; i < count; i++) {
            boolean shown = i == 0 || i == count - 1 || Math.abs(i - page) <= 1;
            if (!shown) {
                continue;
            }
            if (last >= 0 && i - last > 1) {
                Span gap = new Span("…");
                gap.getStyle().set("padding", "0 var(--lumo-space-xs)").set("color", "var(--lumo-secondary-text-color)");
                pages.add(gap);
            }
            int target = i;
            Button number = new Button(String.valueOf(i + 1), e -> go(target));
            number.addThemeVariants(i == page ? ButtonVariant.LUMO_PRIMARY : ButtonVariant.LUMO_TERTIARY);
            number.getStyle().set("min-width", "2.5rem").set("margin", "0").set("padding", "0 var(--lumo-space-s)");
            number.setAriaLabel(Tr.t("Pagina {0}", i + 1));
            if (i == page) {
                number.getElement().setAttribute("aria-current", "page");
            }
            pages.add(number);
            last = i;
        }
    }
}
