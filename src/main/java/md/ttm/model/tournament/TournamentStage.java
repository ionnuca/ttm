package md.ttm.model.tournament;

/**
 * Etapa unui turneu „Grupe + finale”.
 */
public enum TournamentStage {
    /** Etapa 1: grupele. */
    GROUPS("Etapa 1 — Grupe"),
    /** Etapa 2: Finala 1 și Finala 2. */
    FINALS("Etapa 2 — Finale");

    private final String label;

    TournamentStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
