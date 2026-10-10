package md.ttm.ui.match;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.RouterLink;
import md.ttm.model.player.Player;
import md.ttm.service.match.MatchSummary;
import md.ttm.ui.components.Badges;
import md.ttm.ui.player.PlayerView;
import md.ttm.ui.tournament.TournamentView;

import java.time.format.DateTimeFormatter;
import java.util.List;

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
        getStyle().set("display", "flex").set("flex-direction", "column").set("width", "100%");
        for (MatchSummary match : matches) {
            add(perspectivePlayerId == null ? neutralRow(match) : perspectiveRow(match, perspectivePlayerId));
        }
    }

    private static Component neutralRow(MatchSummary match) {
        Div line = new Div(
                name(match.playerA(), match.aWon(), match.rated() ? match.ratingDeltaA() : null, true),
                score(match.walkover() ? (match.aWon() ? "W : L" : "L : W") : match.setsA() + " : " + match.setsB(),
                        match.walkover()),
                name(match.playerB(), !match.aWon(), match.rated() ? match.ratingDeltaB() : null, false));
        line.getStyle()
                .set("display", "grid")
                .set("grid-template-columns", "1fr auto 1fr")
                .set("align-items", "center")
                .set("gap", "var(--lumo-space-s)");
        return row(meta(match), line);
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

    private static Component name(Player player, boolean winner, Integer delta, boolean alignEnd) {
        RouterLink link = new RouterLink(player.getDisplayName(), PlayerView.class, player.getId());
        link.getStyle().set("font-weight", winner ? "700" : "400");
        Span cell = new Span(link);
        if (delta != null) {
            Span d = delta(delta);
            d.getStyle().set(alignEnd ? "margin-left" : "margin-right", "var(--lumo-space-xs)");
            if (alignEnd) {
                cell.add(d);
            } else {
                cell.addComponentAsFirst(d);
            }
        }
        cell.getStyle().set("text-align", alignEnd ? "right" : "left").set("min-width", "0")
                .set("overflow-wrap", "anywhere");
        return cell;
    }

    private static Span score(String text, boolean walkover) {
        Span score = walkover ? Badges.badge(text, Badges.Tone.ERROR) : new Span(text);
        score.getStyle().set("font-weight", "700").set("min-width", "3.2rem").set("text-align", "center")
                .set("justify-content", "center");
        if (walkover) {
            score.getElement().setAttribute("title", "Victorie tehnică");
        }
        return score;
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
