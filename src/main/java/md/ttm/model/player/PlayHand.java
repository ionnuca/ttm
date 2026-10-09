package md.ttm.model.player;

/**
 * Mâna cu care joacă jucătorul.
 */
public enum PlayHand {
    RIGHT("Dreapta"),
    LEFT("Stânga");

    private final String label;

    PlayHand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
