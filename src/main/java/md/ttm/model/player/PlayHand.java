package md.ttm.model.player;

import md.ttm.i18n.Tr;
/**
 * Mâna cu care joacă jucătorul.
 */
public enum PlayHand {
    RIGHT(Tr.key("Dreapta")),
    LEFT(Tr.key("Stânga"));

    private final String label;

    PlayHand(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
