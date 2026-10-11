package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
/**
 * Sistemul de desfășurare a turneului.
 */
public enum TournamentFormat {
    /** O singură grupă, fiecare cu fiecare. */
    ROUND_ROBIN(Tr.key("Round robin")),
    /** Etapa 1: grupe Round Robin; etapa 2: Finala 1 și Finala 2, tot Round Robin. */
    GROUPS_FINALS(Tr.key("Grupe + finale"));

    private final String label;

    TournamentFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
