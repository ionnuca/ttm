package md.ttm.service.tournament;

import md.ttm.model.tournament.Tournament;

import java.time.LocalDate;

/**
 * Datele introduse la crearea/editarea unui turneu: doar numele și data.
 * Configurarea (tip, seturi, turneu comercial) se stabilește la începerea turneului,
 * vezi {@link TournamentSettings}.
 */
public class TournamentForm {

    private String name = "";
    private LocalDate date = LocalDate.now();

    public TournamentForm() {
    }

    public TournamentForm(String name, LocalDate date) {
        this.name = name;
        this.date = date;
    }

    public static TournamentForm from(Tournament tournament) {
        return new TournamentForm(tournament.getName(), tournament.getTournamentDate());
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
}
