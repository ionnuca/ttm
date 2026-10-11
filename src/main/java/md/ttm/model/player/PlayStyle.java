package md.ttm.model.player;

import md.ttm.i18n.Tr;
/**
 * Stilul de joc al unui jucător.
 */
public enum PlayStyle {
    ATTACK(Tr.key("Atac")),
    DEFENCE(Tr.key("Apărare"));

    private final String label;

    PlayStyle(String label) {
        this.label = label;
    }

    public String getLabel() {
        return Tr.t(label);
    }
}
