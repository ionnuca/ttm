package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentFormat;

import java.math.BigDecimal;

/**
 * Configurarea aleasă la începerea turneului: tipul, numărul de seturi și,
 * opțional, turneul comercial (numărul de câștigători și taxa de participare).
 */
public class TournamentSettings {

    private TournamentFormat format = TournamentFormat.ROUND_ROBIN;
    private Integer bestOf = Tournament.DEFAULT_BEST_OF;
    private boolean commercial;
    private Integer winnersCount = 1;
    private BigDecimal entryFee;

    public static TournamentSettings roundRobin(int bestOf) {
        TournamentSettings settings = new TournamentSettings();
        settings.setBestOf(bestOf);
        return settings;
    }

    public static TournamentSettings commercial(int bestOf, int winners, BigDecimal entryFee) {
        TournamentSettings settings = roundRobin(bestOf);
        settings.setCommercial(true);
        settings.setWinnersCount(winners);
        settings.setEntryFee(entryFee);
        return settings;
    }

    public TournamentFormat getFormat() {
        return format;
    }

    public void setFormat(TournamentFormat format) {
        this.format = format;
    }

    public Integer getBestOf() {
        return bestOf;
    }

    public void setBestOf(Integer bestOf) {
        this.bestOf = bestOf;
    }

    public boolean isCommercial() {
        return commercial;
    }

    public void setCommercial(boolean commercial) {
        this.commercial = commercial;
    }

    public Integer getWinnersCount() {
        return winnersCount;
    }

    public void setWinnersCount(Integer winnersCount) {
        this.winnersCount = winnersCount;
    }

    public BigDecimal getEntryFee() {
        return entryFee;
    }

    public void setEntryFee(BigDecimal entryFee) {
        this.entryFee = entryFee;
    }
}
