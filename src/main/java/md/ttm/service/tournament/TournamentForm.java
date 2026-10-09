package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Datele din formularul de creare/editare a unui turneu.
 */
public class TournamentForm {

    private String name = "";
    private LocalDate date = LocalDate.now();
    private int bestOf = Tournament.DEFAULT_BEST_OF;
    private boolean commercial;
    private Integer winnersCount = 1;
    private BigDecimal entryFee;

    public static TournamentForm from(Tournament tournament) {
        TournamentForm form = new TournamentForm();
        form.setName(tournament.getName());
        form.setDate(tournament.getTournamentDate());
        form.setBestOf(tournament.getBestOf());
        form.setCommercial(tournament.isCommercial());
        form.setWinnersCount(tournament.getWinnersCount() != null ? tournament.getWinnersCount() : 1);
        form.setEntryFee(tournament.getEntryFee());
        return form;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getBestOf() {
        return bestOf;
    }

    public void setBestOf(int bestOf) {
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
