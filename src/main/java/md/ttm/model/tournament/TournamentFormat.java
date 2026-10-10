package md.ttm.model.tournament;

/**
 * Sistemul de desfășurare a turneului.
 */
public enum TournamentFormat {
    /** O singură grupă, fiecare cu fiecare. */
    ROUND_ROBIN("Round robin"),
    /** Etapa 1: grupe Round Robin; etapa 2: Finala 1 și Finala 2, tot Round Robin. */
    GROUPS_FINALS("Grupe + finale");

    private final String label;

    TournamentFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
