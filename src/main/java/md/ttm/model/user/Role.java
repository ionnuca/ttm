package md.ttm.model.user;

/**
 * Rolurile conturilor. Vizitatorii neautentificați (Guest) nu au cont, deci nici rol.
 */
public enum Role {
    USER("Utilizator"),
    /** Creează, pornește și conduce turneele; nu are acces la utilizatori și la datele de contact ale jucătorilor. */
    TOURNAMENT_MANAGER("Manager de turnee"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
