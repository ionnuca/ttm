package md.ttm.ui.tournament;

import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.StandingsCalculator;

import java.util.List;
import java.util.Map;

/**
 * Tabelul Round Robin în formă de matrice. Fiecare celulă arată, sub formă de fracție,
 * punctele obținute (sus) și scorul la seturi (jos), din perspectiva jucătorului de pe rând.
 * Ultimele coloane: seturi câștigate/pierdute, puncte și locul în clasament.
 */
class ResultsMatrix extends Div {

    private static final String BORDER = "1px solid var(--lumo-contrast-20pct)";

    /**
     * @param participants participanții în ordinea din grupă
     * @param standings    rândurile calculate, în aceeași ordine
     * @param highlighted  participantul evidențiat (utilizatorul curent), poate fi {@code null}
     */
    ResultsMatrix(List<TournamentParticipant> participants, List<StandingsCalculator.Row> standings,
                  Long highlighted) {
        getStyle()
                .set("overflow-x", "auto")
                .set("max-width", "100%")
                .set("border", BORDER)
                .set("border-radius", "var(--lumo-border-radius-l)");

        Element table = new Element("table");
        table.getStyle()
                .set("border-collapse", "collapse")
                .set("min-width", "100%")
                .set("font-size", "var(--lumo-font-size-s)");

        Element head = new Element("tr");
        head.appendChild(headerCell("#"), headerCell("Jucător").setAttribute("style", headerStyle() + "text-align:left;"));
        for (TournamentParticipant p : participants) {
            head.appendChild(headerCell(String.valueOf(p.getSeed())));
        }
        head.appendChild(headerCell("Seturi"), headerCell("Puncte"), headerCell("Loc"));
        table.appendChild(new Element("thead").appendChild(head));

        Element body = new Element("tbody");
        for (int i = 0; i < participants.size(); i++) {
            TournamentParticipant participant = participants.get(i);
            StandingsCalculator.Row row = standings.get(i);
            boolean own = highlighted != null && highlighted.equals(participant.getId());

            Element tr = new Element("tr");
            if (own) {
                tr.getStyle().set("background", "var(--lumo-primary-color-10pct)");
            }
            tr.appendChild(cell(String.valueOf(participant.getSeed()), "color:var(--lumo-secondary-text-color);"));
            tr.appendChild(nameCell(participant));

            Map<Long, StandingsCalculator.Cell> cells = row.cells();
            for (TournamentParticipant opponent : participants) {
                if (opponent.getId().equals(participant.getId())) {
                    tr.appendChild(cell("", "background:var(--lumo-contrast-20pct);"));
                } else {
                    tr.appendChild(resultCell(cells.get(opponent.getId())));
                }
            }
            tr.appendChild(cell(row.setsWon() + "/" + row.setsLost(), "white-space:nowrap;"));
            tr.appendChild(cell(String.valueOf(row.points()), "font-weight:600;"));
            tr.appendChild(cell(row.place() > 0 ? String.valueOf(row.place()) : "–",
                    "font-weight:700;color:var(--lumo-primary-text-color);"));
            body.appendChild(tr);
        }
        table.appendChild(body);
        getElement().appendChild(table);
    }

    private static Element nameCell(TournamentParticipant participant) {
        Element td = cell(null, "text-align:left;white-space:nowrap;");
        Element name = new Element("div");
        name.setText(participant.getPlayer().getDisplayName());
        name.getStyle().set("font-weight", "500");
        Element rating = new Element("div");
        rating.setText("rating " + participant.getSeedRating());
        rating.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        td.appendChild(name, rating);
        return td;
    }

    private static Element resultCell(StandingsCalculator.Cell result) {
        if (result == null) {
            return cell("", "min-width:3.2rem;");
        }
        String background = result.won()
                ? "var(--lumo-success-color-10pct)"
                : (result.walkover() ? "var(--lumo-error-color-10pct)" : "transparent");
        Element td = cell(null, "min-width:3.2rem;background:" + background + ";");

        Element fraction = new Element("div");
        fraction.getStyle()
                .set("display", "inline-flex")
                .set("flex-direction", "column")
                .set("align-items", "center")
                .set("line-height", "1.25");
        Element points = new Element("span");
        points.setText(String.valueOf(result.points()));
        points.getStyle()
                .set("font-weight", "700")
                .set("padding", "0 0.4em")
                .set("border-bottom", "1px solid currentColor");
        Element score = new Element("span");
        score.setText(result.score());
        score.getStyle().set("color", "var(--lumo-secondary-text-color)");
        fraction.appendChild(points, score);
        td.appendChild(fraction);
        return td;
    }

    private static Element headerCell(String text) {
        Element th = new Element("th");
        th.setText(text);
        th.setAttribute("style", headerStyle());
        return th;
    }

    private static String headerStyle() {
        return "padding:var(--lumo-space-xs) var(--lumo-space-s);border:" + BORDER + ";"
                + "background:var(--lumo-contrast-5pct);font-weight:600;text-align:center;white-space:nowrap;";
    }

    private static Element cell(String text, String extraStyle) {
        Element td = new Element("td");
        if (text != null) {
            td.setText(text);
        }
        td.setAttribute("style", "padding:var(--lumo-space-xs) var(--lumo-space-s);border:" + BORDER + ";"
                + "text-align:center;vertical-align:middle;" + extraStyle);
        return td;
    }
}
