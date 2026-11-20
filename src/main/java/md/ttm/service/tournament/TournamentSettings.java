package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.KnockoutType;
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
    private Integer groupCount = 2;
    /** La o etapă pe tablou ({@link TournamentFormat#KNOCKOUT}): olimpic sau cu ramura de jos. */
    private KnockoutType knockoutType;

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

    public static TournamentSettings groups(int bestOf, int groupCount) {
        TournamentSettings settings = roundRobin(bestOf);
        settings.setFormat(TournamentFormat.GROUPS_FINALS);
        settings.setGroupCount(groupCount);
        return settings;
    }

    /** „Grupe + eliminare”: grupele etapei 1 (minimum una); tabloul se alege la începerea etapei 2. */
    public static TournamentSettings groupsKnockout(int bestOf, int groupCount) {
        TournamentSettings settings = roundRobin(bestOf);
        settings.setFormat(TournamentFormat.GROUPS_KNOCKOUT);
        settings.setGroupCount(groupCount);
        return settings;
    }

    /** Două etape: grupele etapei 1 (minimum una); tipul etapei 2 se alege la începerea ei. */
    public static TournamentSettings twoStages(int bestOf, int groupCount) {
        TournamentSettings settings = roundRobin(bestOf);
        settings.setFormat(TournamentFormat.GROUPS);
        settings.setGroupCount(groupCount);
        return settings;
    }

    /** O etapă pe tablou eliminatoriu, cu toți jucătorii. */
    public static TournamentSettings knockout(int bestOf, KnockoutType type) {
        TournamentSettings settings = roundRobin(bestOf);
        settings.setFormat(TournamentFormat.KNOCKOUT);
        settings.setKnockoutType(type);
        return settings;
    }

    public KnockoutType getKnockoutType() {
        return knockoutType;
    }

    public void setKnockoutType(KnockoutType knockoutType) {
        this.knockoutType = knockoutType;
    }

    public Integer getGroupCount() {
        return groupCount;
    }

    public void setGroupCount(Integer groupCount) {
        this.groupCount = groupCount;
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
