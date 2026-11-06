package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
/**
 * Etapa unui turneu cu grupe („Grupe + finale” sau „Grupe + eliminare”).
 */
public enum TournamentStage {
    /** Etapa 1: grupele. */
    GROUPS(Tr.key("Etapa 1 — Grupe")),
    /** Etapa 2: Finala 1 și Finala 2. */
    FINALS(Tr.key("Etapa 2 — Finale")),
    /** Etapa 2 la „Grupe + eliminare”: tabloul eliminatoriu. */
    KNOCKOUT(Tr.key("Etapa 2 — Tablou eliminatoriu"));

    private final String label;

    TournamentStage(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
