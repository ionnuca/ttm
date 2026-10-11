package md.ttm.model.user;

import md.ttm.i18n.Tr;
/**
 * Rolurile conturilor. Vizitatorii neautentificați (Guest) nu au cont, deci nici rol.
 */
public enum Role {
    USER(Tr.key("Utilizator")),
    /** Creează, pornește și conduce turneele; nu are acces la utilizatori și la datele de contact ale jucătorilor. */
    TOURNAMENT_MANAGER(Tr.key("Manager de turnee")),
    ADMIN(Tr.key("Administrator"));

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
