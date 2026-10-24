package md.ttm.model.tournament;

import md.ttm.i18n.Tr;

/** Tipul tabloului eliminatoriu (etapa 2 la „Grupe + eliminare”). */
public enum KnockoutType {
    /** Sistem olimpic: cine pierde iese din turneu. */
    SINGLE(Tr.key("Olimpic: semifinaliștii învinși joacă pentru locul 3")),
    /** Sistem olimpic cu ramura de jos (dublă eliminare): după prima înfrângere jucătorul trece în ramura de jos. */
    DOUBLE(Tr.key("Olimpic cu ramura de jos: învinșii joacă în ramura de jos pentru locul 3"));

    private final String label;

    KnockoutType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
