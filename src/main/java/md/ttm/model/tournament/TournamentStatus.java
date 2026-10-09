package md.ttm.model.tournament;

/**
 * Etapele unui turneu.
 */
public enum TournamentStatus {
    /** Turneul e creat; jucătorii se pot înscrie. */
    REGISTRATION("Înscriere deschisă"),
    /** Turneul a început: grupa și meciurile sunt generate, se introduc rezultatele. */
    IN_PROGRESS("În desfășurare"),
    /** Toate meciurile au rezultat. */
    FINISHED("Încheiat");

    private final String label;

    TournamentStatus(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
