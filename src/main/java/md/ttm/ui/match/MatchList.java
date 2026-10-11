package md.ttm.ui.match;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;
import md.ttm.model.player.Player;
import md.ttm.service.match.MatchSummary;
import md.ttm.ui.components.Badges;
import md.ttm.ui.components.PlayerAvatar;
import md.ttm.ui.player.PlayerView;
import md.ttm.ui.tournament.TournamentView;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Lista de meciuri: data, turneul (și grupa), cei doi jucători și scorul.
 * Cu un jucător dat, fiecare meci e arătat din perspectiva lui: victorie / înfrângere, adversarul, scorul lui.
 */
public class MatchList extends Div {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    /**
     * @param perspectivePlayerId jucătorul din perspectiva căruia se arată meciurile; {@code null} = neutru
     */
    public MatchList(List<MatchSummary> matches, Long perspectivePlayerId) {
        this(matches, perspectivePlayerId, Map.of());
    }

    /**
     * @param photoVersions versiunile pozelor jucătorilor (pentru avatarele din cardurile meciurilor)
     */
    public MatchList(List<MatchSummary> matches, Long perspectivePlayerId, Map<Long, Long> photoVersions) {
        if (perspectivePlayerId == null) {
            // carduri ca o tabelă de scor, câte încap pe un rând
            getStyle()
                    .set("display", "grid")
                    .set("grid-template-columns", "repeat(auto-fill, minmax(min(100%, 19rem), 1fr))")
                    .set("gap", "var(--lumo-space-m)")
                    .set("width", "100%");
            for (MatchSummary match : matches) {
                add(card(match, photoVersions));
            }
        } else {
            getStyle().set("display", "flex").set("flex-direction", "column").set("width", "100%");
            for (MatchSummary match : matches) {
                add(perspectiveRow(match, perspectivePlayerId));
            }
        }
    }

    /** Un meci ca tabelă de scor: data și turneul sus, apoi câte un rând pentru fiecare jucător. */
    private static Component card(MatchSummary match, Map<Long, Long> photoVersions) {
        Component meta = meta(match);
        Div head = new Div(meta);
        head.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)")
                .set("justify-content", "space-between");
        if (match.walkover()) {
            Span walkover = Badges.badge("tehnic", Badges.Tone.ERROR);
            walkover.getElement().setAttribute("title", "Victorie tehnică: adversarul nu a jucat");
            head.add(walkover);
        }
        Div card = new Div(head,
                scoreLine(match.playerA(), match.aWon(), match.walkover() ? (match.aWon() ? "W" : "–")
                        : String.valueOf(match.setsA()), match.rated() ? match.ratingDeltaA() : null, photoVersions),
                scoreLine(match.playerB(), !match.aWon(), match.walkover() ? (match.aWon() ? "–" : "W")
                        : String.valueOf(match.setsB()), match.rated() ? match.ratingDeltaB() : null, photoVersions));
        card.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "var(--lumo-space-xs)")
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m) var(--lumo-space-m)")
                .set("border", "1px solid var(--lumo-contrast-10pct)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-base-color)")
                .set("min-width", "0");
        return card;
    }

    private static Component scoreLine(Player player, boolean winner, String score, Integer delta,
                                       Map<Long, Long> photoVersions) {
        RouterLink name = new RouterLink(player.getDisplayName(), PlayerView.class, player.getId());
        name.getStyle()
                .set("font-weight", winner ? "700" : "400")
                .set("color", winner ? "var(--lumo-body-text-color)" : "var(--lumo-secondary-text-color)")
                .set("flex", "1")
                .set("min-width", "0")
                .set("overflow", "hidden")
                .set("text-overflow", "ellipsis")
                .set("white-space", "nowrap");
        Span sets = new Span(score);
        sets.getStyle()
                .set("font-size", "var(--lumo-font-size-l)")
                .set("font-weight", winner ? "700" : "400")
                .set("min-width", "1.5rem")
                .set("text-align", "right")
                .set("font-variant-numeric", "tabular-nums");
        Span change = delta == null ? new Span() : delta(delta);
        change.getStyle().set("min-width", "2.4rem").set("text-align", "right");
        Div line = new Div(PlayerAvatar.of(player, photoVersions.get(player.getId()), "1.75rem"), name, sets, change);
        line.getStyle()
                .set("display", "flex")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)");
        return line;
    }

    private static Component perspectiveRow(MatchSummary match, Long playerId) {
        MatchSummary.Perspective p = match.from(playerId);
        Span outcome = Badges.badge(p.won() ? "V" : "Î", p.won() ? Badges.Tone.SUCCESS : Badges.Tone.ERROR);
        outcome.getElement().setAttribute("title", p.won() ? "Victorie" : "Înfrângere");
        outcome.getStyle().set("min-width", "1.6rem").set("justify-content", "center");

        Span versus = new Span("vs ");
        versus.getStyle().set("color", "var(--lumo-secondary-text-color)");
        RouterLink opponent = new RouterLink(p.opponent().getDisplayName(), PlayerView.class, p.opponent().getId());
        Span who = new Span(versus, opponent);
        who.getStyle().set("flex", "1").set("min-width", "0").set("overflow", "hidden")
                .set("text-overflow", "ellipsis").set("white-space", "nowrap");

        Span result = match.walkover()
                ? Badges.badge(p.won() ? "victorie tehnică" : "înfrângere tehnică", Badges.Tone.CONTRAST)
                : new Span(p.ownSets() + " : " + p.opponentSets());
        result.getStyle().set("font-weight", "700");

        Div line = new Div(outcome, who, result);
        if (match.rated() && p.ratingDelta() != null) {
            line.add(delta(p.ratingDelta()));
        }
        line.getStyle().set("display", "flex").set("align-items", "center").set("gap", "var(--lumo-space-s)");
        return row(meta(match), line);
    }

    private static Component meta(MatchSummary match) {
        Span date = new Span(DATE.format(match.tournamentDate()) + " · ");
        RouterLink tournament = new RouterLink(match.tournamentName(), TournamentView.class, match.tournamentId());
        Span meta = new Span(date, tournament);
        if (match.stage() != null) {
            meta.add(new Span(" · " + match.stage()));
        }
        meta.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("color", "var(--lumo-secondary-text-color)");
        return meta;
    }

    private static Component row(Component meta, Component line) {
        Div row = new Div(meta, line);
        row.getStyle()
                .set("display", "flex")
                .set("flex-direction", "column")
                .set("gap", "2px")
                .set("padding", "var(--lumo-space-s) 0")
                .set("border-top", "1px solid var(--lumo-contrast-10pct)");
        return row;
    }



    private static Span delta(int delta) {
        Span span = new Span(delta > 0 ? "+" + delta : String.valueOf(delta));
        span.getStyle()
                .set("font-size", "var(--lumo-font-size-xs)")
                .set("font-weight", "600")
                .set("color", delta > 0 ? "var(--lumo-success-text-color)"
                        : delta < 0 ? "var(--lumo-error-text-color)" : "var(--lumo-secondary-text-color)");
        return span;
    }
}
