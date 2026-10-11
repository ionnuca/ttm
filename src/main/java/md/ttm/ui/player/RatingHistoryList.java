package md.ttm.ui.player;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.RouterLink;
import md.ttm.model.rating.RatingHistory;
import md.ttm.ui.tournament.TournamentView;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Evoluția ratingului unui jucător: ratingul înainte și după fiecare turneu încheiat.
 */
public class RatingHistoryList extends VerticalLayout {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public RatingHistoryList(List<RatingHistory> history, int initialRating) {
        setPadding(false);
        add(new H3(Tr.t("Evoluția ratingului")));

        if (history.isEmpty()) {
            add(new Paragraph(Tr.t("Ratingul se modifică după fiecare turneu încheiat. Ratingul inițial: {0}.", initialRating)));
            return;
        }

        Grid<RatingHistory> grid = new Grid<>();
        grid.addThemeVariants(GridVariant.LUMO_ROW_STRIPES, GridVariant.LUMO_COMPACT);
        grid.setAllRowsVisible(true);
        grid.addColumn(h -> DATE.format(h.getTournament().getTournamentDate()))
                .setHeader(Tr.t("Data")).setAutoWidth(true).setFlexGrow(0);
        grid.addComponentColumn(h -> new RouterLink(h.getTournament().getName(), TournamentView.class,
                        h.getTournament().getId()))
                .setHeader(Tr.t("Turneu")).setFlexGrow(2);
        grid.addColumn(RatingHistory::getRatingBefore)
                .setHeader(Tr.t("Înainte")).setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addColumn(RatingHistory::getRatingAfter)
                .setHeader(Tr.t("După")).setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.addComponentColumn(h -> change(h.getChange()))
                .setHeader(Tr.t("Schimbare")).setAutoWidth(true).setFlexGrow(0).setTextAlign(ColumnTextAlign.END);
        grid.setItems(history);

        Span note = new Span(Tr.t("Ratingul inițial: {0}. Calcul Elo: K = 40 în primele 30 de meciuri, apoi 20; victoriile tehnice nu modifică ratingul.", initialRating));
        note.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        add(grid, note);
    }

    /** Schimbarea colorată: verde pentru plus, roșu pentru minus. */
    public static Span change(int delta) {
        Span span = new Span(delta > 0 ? "+" + delta : String.valueOf(delta));
        span.getStyle()
                .set("font-weight", "600")
                .set("color", delta > 0 ? "var(--lumo-success-text-color)"
                        : delta < 0 ? "var(--lumo-error-text-color)" : "var(--lumo-secondary-text-color)");
        return span;
    }
}
