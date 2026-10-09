package md.ttm.ui.components;

import com.vaadin.flow.component.html.Span;

/**
 * Etichete colorate mici (stil de joc, stare cont, stare turneu), folosind culorile temei Lumo.
 */
public final class Badges {

    public enum Tone {
        PRIMARY("--lumo-primary-text-color", "--lumo-primary-color-10pct"),
        SUCCESS("--lumo-success-text-color", "--lumo-success-color-10pct"),
        ERROR("--lumo-error-text-color", "--lumo-error-color-10pct"),
        CONTRAST("--lumo-secondary-text-color", "--lumo-contrast-10pct");

        private final String color;
        private final String background;

        Tone(String color, String background) {
            this.color = color;
            this.background = background;
        }
    }

    private Badges() {
    }

    public static Span badge(String text, Tone tone) {
        Span badge = new Span(text);
        badge.getStyle()
                .set("display", "inline-block")
                .set("padding", "0 var(--lumo-space-s)")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("font-weight", "500")
                .set("line-height", "1.6")
                .set("white-space", "nowrap")
                .set("color", "var(" + tone.color + ")")
                .set("background", "var(" + tone.background + ")");
        return badge;
    }
}
