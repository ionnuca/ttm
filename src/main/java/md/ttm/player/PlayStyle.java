package md.ttm.player;

/**
 * Stilul de joc al unui jucător.
 */
public enum PlayStyle {
    ATTACK("Atac"),
    DEFENCE("Apărare");

    private final String label;

    PlayStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
