package md.ttm.model.tournament;

/**
 * Sistemul de desfășurare a turneului. Deocamdată doar Round Robin (fiecare cu fiecare).
 */
public enum TournamentFormat {
    ROUND_ROBIN("Round robin");

    private final String label;

    TournamentFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
