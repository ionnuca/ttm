package md.ttm.common;

/**
 * Funcții mici pentru curățarea textului introdus de utilizator.
 */
public final class Texts {

    private Texts() {
    }

    /** Elimină spațiile de la capete; un text gol devine {@code null}. */
    public static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
