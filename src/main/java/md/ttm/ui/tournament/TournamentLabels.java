package md.ttm.ui.tournament;

import com.vaadin.flow.component.html.Span;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentStatus;
import md.ttm.ui.components.Badges;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import md.ttm.i18n.Tr;

/**
 * Textele afișate pentru turnee: date, sume, configurare, stare.
 */
public final class TournamentLabels {

    private TournamentLabels() {
    }

    /** Data lungă în limba interfeței, de ex. „10 octombrie 2026” / „10 октября 2026” / „10 October 2026”. */
    public static String longDate(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofPattern("d MMMM yyyy", Tr.locale()).format(date);
    }

    public static String shortDate(LocalDate date) {
        return date == null ? "" : DateTimeFormatter.ofPattern("dd.MM.yyyy").format(date);
    }

    /** Titlul unui premiu, construit în română de serviciu („Locul 2”, „Finala 1 · locul 1”), în limba interfeței. */
    public static String prizeTitle(String stored) {
        java.util.regex.Matcher finalPlace = java.util.regex.Pattern.compile("Finala (\\d+) · locul (\\d+)").matcher(stored);
        if (finalPlace.matches()) {
            return Tr.t("Finala {0} · locul {1}", finalPlace.group(1), finalPlace.group(2));
        }
        java.util.regex.Matcher place = java.util.regex.Pattern.compile("Locul (\\d+)").matcher(stored);
        if (place.matches()) {
            return Tr.t("Locul {0}", place.group(1));
        }
        return stored;
    }

    /** Numele unei grupe sau finale, salvat în română („Grupa A”, „Finala 1”), în limba interfeței. */
    public static String groupName(String stored) {
        if (stored == null) {
            return null;
        }
        if (stored.startsWith("Grupa ")) {
            return Tr.t("Grupa {0}", stored.substring("Grupa ".length()));
        }
        if (stored.startsWith("Finala ")) {
            return Tr.t("Finala {0}", stored.substring("Finala ".length()));
        }
        if (stored.equals("Tablou eliminatoriu")) {
            return Tr.t("Tablou eliminatoriu");
        }
        return stored;
    }

    /** De exemplu „1.250,50 lei” sau „800 lei”. */
    public static String money(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(Tr.locale()));
        return Tr.t("{0} lei", format.format(amount));
    }

    /** De exemplu „Round robin · best of 5”. */
    static String configuration(Tournament tournament) {
        String format = tournament.getFormat().getLabel();
        if (tournament.isGroupsFormat() && tournament.getGroupCount() != null) {
            format += " " + Tr.t("({0} grupe)", tournament.getGroupCount());
        }
        return format + " · best of " + tournament.getBestOf();
    }

    static Span statusBadge(TournamentStatus status) {
        Badges.Tone tone = switch (status) {
            case REGISTRATION -> Badges.Tone.PRIMARY;
            case IN_PROGRESS -> Badges.Tone.SUCCESS;
            case FINISHED -> Badges.Tone.CONTRAST;
        };
        return Badges.badge(status.getLabel(), tone);
    }
}
