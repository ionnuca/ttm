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
import java.util.Locale;

/**
 * Textele afișate pentru turnee: date, sume, configurare, stare.
 */
final class TournamentLabels {

    static final Locale RO = Locale.forLanguageTag("ro");
    private static final DateTimeFormatter LONG_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", RO);
    private static final DateTimeFormatter SHORT_DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy", RO);

    private TournamentLabels() {
    }

    static String longDate(LocalDate date) {
        return date == null ? "" : LONG_DATE.format(date);
    }

    static String shortDate(LocalDate date) {
        return date == null ? "" : SHORT_DATE.format(date);
    }

    /** De exemplu „1.250,50 lei” sau „800 lei”. */
    static String money(BigDecimal amount) {
        if (amount == null) {
            return "—";
        }
        DecimalFormat format = new DecimalFormat("#,##0.##", DecimalFormatSymbols.getInstance(RO));
        return format.format(amount) + " lei";
    }

    static String bestOf(int bestOf) {
        return "Best of " + bestOf + " (" + (bestOf / 2 + 1) + " seturi câștigătoare)";
    }

    /** De exemplu „Round robin · best of 5”. */
    static String configuration(Tournament tournament) {
        return tournament.getFormat().getLabel() + " · best of " + tournament.getBestOf();
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
