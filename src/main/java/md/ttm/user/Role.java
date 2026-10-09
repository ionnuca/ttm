package md.ttm.user;

/**
 * Rolurile conturilor. Vizitatorii neautentificați (Guest) nu au cont, deci nici rol.
 */
public enum Role {
    USER("Utilizator"),
    ADMIN("Administrator");

    private final String label;

    Role(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
