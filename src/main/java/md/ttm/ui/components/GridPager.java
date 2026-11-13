package md.ttm.ui.components;

import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridSortOrder;
import com.vaadin.flow.function.SerializableComparator;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Un {@link Grid} pe pagini: sortarea după o coloană se aplică pe toată lista (nu doar pe pagina curentă),
 * iar tabelul arată doar rândurile paginii, fără derulare proprie.
 */
public final class GridPager<T> {

    private final Grid<T> grid;
    private final Pager pager;
    private List<T> items = List.of();

    public GridPager(Grid<T> grid, Pager pager) {
        this.grid = grid;
        this.pager = pager;
        grid.setAllRowsVisible(true);
        grid.addSortListener(e -> {
            if (e.isFromClient()) {
                pager.setPage(0);
                show();
            }
        });
        pager.addPageChangeListener(p -> {
            show();
            grid.scrollToStart();
            grid.getElement().executeJs("this.scrollIntoView({block: 'nearest'})");
        });
    }

    /** Lista întreagă; pagina curentă rămâne dacă mai există, iar {@code firstPage} întoarce la prima. */
    public void setItems(List<T> items, boolean firstPage) {
        this.items = items;
        if (firstPage) {
            pager.setPage(0);
        }
        pager.setTotal(items.size());
        show();
    }

    private void show() {
        Comparator<T> order = null;
        for (GridSortOrder<T> sort : grid.getSortOrder()) {
            SerializableComparator<T> c = sort.getSorted().getComparator(sort.getDirection());
            if (c != null) {
                order = order == null ? c : order.thenComparing(c);
            }
        }
        List<T> sorted = items;
        if (order != null) {
            sorted = new ArrayList<>(items);
            sorted.sort(order);
        }
        grid.setItems(pager.slice(sorted));
    }
}
