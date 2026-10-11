package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
/**
 * Etapa unui turneu „Grupe + finale”.
 */
public enum TournamentStage {
    /** Etapa 1: grupele. */
    GROUPS(Tr.key("Etapa 1 — Grupe")),
    /** Etapa 2: Finala 1 și Finala 2. */
    FINALS(Tr.key("Etapa 2 — Finale"));

    private final String label;

    TournamentStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
