package md.ttm.ui.tournament;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import md.ttm.i18n.Tr;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.Bracket;
import md.ttm.service.tournament.KnockoutView;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Tabloul eliminatoriu desenat: câte o coloană pentru fiecare tur, meciurile aliniate ca într-un arbore
 * și unite prin linii. Pe ecrane înguste se derulează lateral.
 */
final class BracketDiagram {

    /** Stilurile diagramei; se adaugă o singură dată în pagină. */
    static final String STYLE = """
            <style>
            .ttm-bracket { display: flex; overflow-x: auto; padding-bottom: var(--lumo-space-s); width: 100%;
                           -webkit-overflow-scrolling: touch; }
            .ttm-bracket-col { display: flex; flex-direction: column; flex: 0 0 auto; width: 13.5rem; }
            .ttm-bracket-head { font-size: var(--lumo-font-size-s); font-weight: 600; color: var(--lumo-secondary-text-color);
                                text-align: center; padding: 0 1rem var(--lumo-space-xs); line-height: 1.25;
                                min-height: 2.5em; display: flex; align-items: flex-end; justify-content: center; }
            .ttm-bracket-slots { display: flex; flex-direction: column; height: var(--ttm-bracket-height); }
            .ttm-bracket-slot { flex: 1 1 0; display: flex; align-items: center; position: relative; padding: 0.3rem 1rem; }
            .ttm-bracket-slot.in::before { content: ''; position: absolute; left: 0; top: 50%; width: 1rem;
                                           border-top: 1px solid var(--lumo-contrast-30pct); }
            .ttm-bracket-slot.out::after { content: ''; position: absolute; right: 0; width: 1rem;
                                           border-color: var(--lumo-contrast-30pct); border-style: solid; border-width: 0; }
            .ttm-bracket-slot.out.straight::after { top: 50%; border-top-width: 1px; }
            .ttm-bracket-slot.out.top::after { top: 50%; bottom: 0; border-top-width: 1px; border-right-width: 1px; }
            .ttm-bracket-slot.out.bottom::after { top: 0; bottom: 50%; border-bottom-width: 1px; border-right-width: 1px; }
            .ttm-bracket-match { width: 100%; border: 1px solid var(--lumo-contrast-20pct); border-radius: var(--lumo-border-radius-m);
                                 background: var(--lumo-base-color); font-size: var(--lumo-font-size-s); overflow: hidden; }
            .ttm-bracket-match.clickable { cursor: pointer; }
            .ttm-bracket-match.clickable:hover { border-color: var(--lumo-primary-color-50pct); }
            .ttm-bracket-match.own { background: var(--lumo-primary-color-10pct); }
            .ttm-bracket-match.hidden { visibility: hidden; }
            .ttm-bracket-line { display: flex; gap: 0.4rem; padding: 0.2rem 0.5rem; line-height: 1.35; }
            .ttm-bracket-line + .ttm-bracket-line { border-top: 1px solid var(--lumo-contrast-10pct); }
            .ttm-bracket-name { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
            .ttm-bracket-score { min-width: 1.2em; text-align: right; font-variant-numeric: tabular-nums; }
            .ttm-bracket-line.winner { font-weight: 600; }
            .ttm-bracket-line.loser, .ttm-bracket-line.unknown { color: var(--lumo-secondary-text-color); }
            .ttm-bracket-line.bye { color: var(--lumo-tertiary-text-color); font-style: italic; }
            @media (max-width: 600px) {
              .ttm-bracket-col { width: 10.75rem; }
              .ttm-bracket-slot { padding: 0.3rem 0.6rem; }
              .ttm-bracket-slot.in::before, .ttm-bracket-slot.out::after { width: 0.6rem; }
            }
            </style>
            """;

    private BracketDiagram() {
    }

    static Component styles() {
        return new Html(STYLE.strip());
    }

    /**
     * @param onOpen la apăsarea unui meci care se joacă (introducerea sau corectarea rezultatului); {@code null} = doar vizualizare
     * @param boxes  aici se trec meciurile desenate, după id, pentru derularea la ele după salvare
     */
    static Component of(KnockoutView view, Bracket.Section section, Long own,
                        Consumer<TournamentMatch> onOpen, Function<Bracket.Match, String> roundLabel,
                        Map<Long, Component> boxes) {
        Map<Integer, List<Bracket.Match>> rounds = view.state().matches().stream()
                .filter(m -> m.section() == section)
                .collect(Collectors.groupingBy(Bracket.Match::round, TreeMap::new, Collectors.toList()));
        Div bracket = new Div();
        bracket.addClassName("ttm-bracket");
        if (rounds.isEmpty()) {
            return bracket;
        }
        int first = rounds.values().iterator().next().size();
        bracket.getStyle().set("--ttm-bracket-height", "calc(" + Math.max(first, 1) + " * 4.6rem)");

        List<List<Bracket.Match>> columns = List.copyOf(rounds.values());
        for (int c = 0; c < columns.size(); c++) {
            List<Bracket.Match> matches = columns.get(c);
            int previous = c > 0 ? columns.get(c - 1).size() : 0;
            int next = c + 1 < columns.size() ? columns.get(c + 1).size() : 0;
            Div column = new Div();
            column.addClassName("ttm-bracket-col");
            Div head = new Div(roundLabel.apply(matches.get(0)));
            head.addClassName("ttm-bracket-head");
            Div slots = new Div();
            slots.addClassName("ttm-bracket-slots");
            for (int i = 0; i < matches.size(); i++) {
                Bracket.Match m = matches.get(i);
                Div slot = new Div();
                slot.addClassName("ttm-bracket-slot");
                if (previous == matches.size() || previous == matches.size() * 2) {
                    slot.addClassName("in");
                }
                if (next == matches.size()) {
                    slot.addClassNames("out", "straight");
                } else if (next * 2 == matches.size()) {
                    slot.addClassNames("out", i % 2 == 0 ? "top" : "bottom");
                }
                TournamentMatch row = view.matches().get(m.key());
                Div box = box(view, m, row, own, onOpen);
                if (row != null) {
                    boxes.put(row.getId(), box);
                }
                slot.add(box);
                slots.add(slot);
            }
            column.add(head, slots);
            bracket.add(column);
        }
        return bracket;
    }

    private static Div box(KnockoutView view, Bracket.Match m, TournamentMatch row, Long own,
                           Consumer<TournamentMatch> onOpen) {
        Div box = new Div();
        box.addClassName("ttm-bracket-match");
        boolean byeA = isBye(m.a());
        boolean byeB = isBye(m.b());
        if ((byeA && byeB) || (byeA && m.b() == null) || (byeB && m.a() == null)) {
            box.addClassName("hidden"); // păstrează locul în arbore
        }
        Integer setsA = row != null && row.isPlayed() && !row.isWalkover() ? row.getSetsA() : null;
        Integer setsB = row != null && row.isPlayed() && !row.isWalkover() ? row.getSetsB() : null;
        String scoreA = setsA != null ? String.valueOf(setsA) : row != null && row.isWalkover() ? (m.winner().equals(m.a()) ? "W" : "–") : "";
        String scoreB = setsB != null ? String.valueOf(setsB) : row != null && row.isWalkover() ? (m.winner().equals(m.b()) ? "W" : "–") : "";
        box.add(line(view, m.a(), m, scoreA), line(view, m.b(), m, scoreB));
        if (own != null && (own.equals(m.a()) || own.equals(m.b()))) {
            box.addClassName("own");
        }
        if (row != null && onOpen != null) {
            box.addClassName("clickable");
            box.getElement().setAttribute("role", "button");
            box.getElement().setAttribute("tabindex", "0");
            box.getElement().setAttribute("title", row.isPlayed() ? Tr.t("Modifică rezultatul") : Tr.t("Introdu rezultatul"));
            box.addClickListener(e -> onOpen.accept(row));
        }
        return box;
    }

    private static Div line(KnockoutView view, Long id, Bracket.Match m, String score) {
        Div line = new Div();
        line.addClassName("ttm-bracket-line");
        Span name = new Span();
        name.addClassName("ttm-bracket-name");
        if (id == null) {
            name.setText("?");
            line.addClassName("unknown");
        } else if (isBye(id)) {
            name.setText(Tr.t("trece direct"));
            line.addClassName("bye");
        } else {
            TournamentParticipant p = view.participant(id);
            name.setText(p != null ? p.getPlayer().getDisplayName() : "?");
            name.getElement().setAttribute("title", name.getText());
            if (m.playable() && m.decided()) {
                line.addClassName(id.equals(m.winner()) ? "winner" : "loser");
            }
        }
        Span sets = new Span(score);
        sets.addClassName("ttm-bracket-score");
        line.add(name, sets);
        return line;
    }

    private static boolean isBye(Long id) {
        return id != null && id == Bracket.BYE;
    }
}
