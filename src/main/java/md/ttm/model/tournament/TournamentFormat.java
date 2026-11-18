package md.ttm.model.tournament;

import md.ttm.i18n.Tr;
/**
 * Sistemul de desfășurare a turneului.
 */
public enum TournamentFormat {
    /** O etapă: o singură grupă, fiecare cu fiecare. */
    ROUND_ROBIN(Tr.key("Round robin")),
    /** O etapă: tablou eliminatoriu cu toți jucătorii, olimpic sau cu ramura de jos ({@link Tournament#getKnockoutType()}). */
    KNOCKOUT(Tr.key("Eliminare")),
    /** Două etape: grupele etapei 1; tipul etapei 2 (finale sau tablou) se alege la începerea ei. */
    GROUPS(Tr.key("Grupe + etapa 2")),
    /** Etapa 1: grupe Round Robin; etapa 2: Finala 1, 2 … (numărul se alege la începerea etapei), tot Round Robin. */
    GROUPS_FINALS(Tr.key("Grupe + finale")),
    /** Etapa 1: una sau mai multe grupe Round Robin; etapa 2: tablou eliminatoriu (olimpic sau cu ramura de jos). */
    GROUPS_KNOCKOUT(Tr.key("Grupe + eliminare"));

    private final String label;

    TournamentFormat(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
