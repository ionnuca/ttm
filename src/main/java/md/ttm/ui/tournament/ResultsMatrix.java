package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Element;
import md.ttm.model.rating.RatingHistory;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.StandingsCalculator;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Tabelul Round Robin în formă de matrice. Fiecare celulă arată, sub formă de fracție,
 * punctele obținute (sus) și scorul la seturi (jos), din perspectiva jucătorului de pe rând.
 * Ultimele coloane: seturi câștigate/pierdute, puncte și locul în clasament.
 * Când rezultatele se pot introduce, un click pe celulă deschide meciul celor doi jucători.
 */
class ResultsMatrix extends Div {

    private static final String BORDER = "1px solid var(--lumo-contrast-20pct)";
    private static final String STICKY = "position:sticky;left:0;z-index:1;";

    /**
     * @param participants participanții în ordinea din grupă (numerotați 1, 2, 3 … în această ordine)
     * @param standings    rândurile calculate, în aceeași ordine
     * @param highlighted  participantul evidențiat (utilizatorul curent), poate fi {@code null}
     * @param ratingChanges ratingul înainte/după turneu, pe jucător (gol dacă turneul nu e încheiat)
     * @param matches      meciurile tabelului (cele preluate din etapa 1 lipsesc și nu se pot deschide)
     * @param onMatchClick deschide editarea meciului din celula apăsată; {@code null} = celulele nu se pot apăsa
     */
    ResultsMatrix(List<TournamentParticipant> participants, List<StandingsCalculator.Row> standings,
                  Long highlighted, Map<Long, RatingHistory> ratingChanges,
                  List<TournamentMatch> matches, Consumer<TournamentMatch> onMatchClick) {
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
        head.appendChild(headerCell(Tr.t("Jucător")).setAttribute("style", headerStyle() + STICKY + "text-align:left;"));
        for (int i = 1; i <= participants.size(); i++) {
            head.appendChild(headerCell(String.valueOf(i)));
        }
        head.appendChild(headerCell(Tr.t("Seturi")), headerCell(Tr.t("Puncte")), headerCell(Tr.t("Loc")));
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
            tr.appendChild(nameCell(i + 1, participant, own, ratingChanges.get(participant.getPlayer().getId())));

            Map<Long, StandingsCalculator.Cell> cells = row.cells();
            for (TournamentParticipant opponent : participants) {
                if (opponent.getId().equals(participant.getId())) {
                    tr.appendChild(cell("", "background:var(--lumo-contrast-20pct);"));
                } else {
                    Element td = resultCell(cells.get(opponent.getId()));
                    if (onMatchClick != null) {
                        matches.stream()
                                .filter(m -> m.involves(participant.getId()) && m.involves(opponent.getId()))
                                .findFirst()
                                .ifPresent(m -> makeClickable(td, m, participant, opponent, onMatchClick));
                    }
                    tr.appendChild(td);
                }
            }
            tr.appendChild(cell(row.setsWon() + "/" + row.setsLost(), "white-space:nowrap;"));
            tr.appendChild(cell(String.valueOf(row.points()), "font-weight:600;"));
            Element place = cell(row.place() > 0 ? String.valueOf(row.place()) : "–",
                    "font-weight:700;color:var(--lumo-primary-text-color);");
            if (row.sharedPlace()) {
                // egalitate nedepartajată: locul e împărțit
                place.setText(row.place() + "=");
                place.setAttribute("title", Tr.t("Loc împărțit: egalitate care nu poate fi departajată prin meciurile directe"));
            }
            tr.appendChild(place);
            body.appendChild(tr);
        }
        table.appendChild(body);
        if (onMatchClick != null) {
            // fără temă proprie: :hover și :focus-visible nu se pot scrie în stilul inline al celulei
            Element style = new Element("style");
            style.setText(".ttm-matrix-cell{cursor:pointer}"
                    + ".ttm-matrix-cell:hover{box-shadow:inset 0 0 0 2px var(--lumo-primary-color-50pct)}"
                    + ".ttm-matrix-cell:focus-visible{outline:2px solid var(--lumo-primary-color);outline-offset:-2px}");
            getElement().appendChild(style);
        }
        getElement().appendChild(table);
    }

    /** Numele rămâne vizibil la derularea orizontală a tabelului (pe telefon). */
    private static Element nameCell(int number, TournamentParticipant participant, boolean own, RatingHistory change) {
        String background = own
                ? "linear-gradient(var(--lumo-primary-color-10pct), var(--lumo-primary-color-10pct)), var(--lumo-base-color)"
                : "var(--lumo-base-color)";
        Element td = cell(null, STICKY + "background:" + background + ";text-align:left;white-space:nowrap;");
        Element name = new Element("div");
        Element seed = new Element("span");
        seed.setText(number + ". ");
        seed.getStyle().set("color", "var(--lumo-secondary-text-color)");
        Element fullName = new Element("span");
        fullName.setText(participant.getPlayer().getDisplayName());
        name.appendChild(seed, fullName);
        name.getStyle().set("font-weight", "500");
        Element rating = new Element("div");
        rating.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        if (change == null) {
            rating.setText(Tr.t("rating {0}", participant.getSeedRating()));
        } else {
            Element values = new Element("span");
            values.setText(Tr.t("rating {0}", change.getRatingBefore() + " → " + change.getRatingAfter()) + " ");
            Element delta = new Element("span");
            int d = change.getChange();
            delta.setText(d > 0 ? "+" + d : String.valueOf(d));
            delta.getStyle()
                    .set("font-weight", "600")
                    .set("color", d > 0 ? "var(--lumo-success-text-color)"
                            : d < 0 ? "var(--lumo-error-text-color)" : "inherit");
            rating.appendChild(values, delta);
        }
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

    private static void makeClickable(Element td, TournamentMatch match, TournamentParticipant participant,
                                      TournamentParticipant opponent, Consumer<TournamentMatch> onMatchClick) {
        String label = match.isPlayed()
                ? Tr.t("Modifică rezultatul {0} – {1}", participant.getPlayer().getDisplayName(), opponent.getPlayer().getDisplayName())
                : Tr.t("Introdu rezultatul {0} – {1}", participant.getPlayer().getDisplayName(), opponent.getPlayer().getDisplayName());
        td.setAttribute("title", label);
        td.setAttribute("aria-label", label);
        td.setAttribute("role", "button");
        td.setAttribute("tabindex", "0");
        td.setAttribute("class", "ttm-matrix-cell");
        td.addEventListener("click", e -> onMatchClick.accept(match));
        td.addEventListener("keydown", e -> onMatchClick.accept(match))
                .setFilter("event.key === 'Enter' || event.key === ' '")
                .preventDefault();
    }

    private static Element headerCell(String text) {
        Element th = new Element("th");
        th.setText(text);
        th.setAttribute("style", headerStyle());
        return th;
    }

    private static String headerStyle() {
        return "padding:var(--lumo-space-xs) var(--lumo-space-s);border:" + BORDER + ";"
                + "background:linear-gradient(var(--lumo-contrast-5pct), var(--lumo-contrast-5pct)), var(--lumo-base-color);"
                + "font-weight:600;text-align:center;white-space:nowrap;";
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
