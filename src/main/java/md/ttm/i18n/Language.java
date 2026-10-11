package md.ttm.i18n;

import java.util.Locale;

/** Limbile interfeței. Româna e limba de bază: textele din cod sunt în română, celelalte sunt traduceri. */
public enum Language {
    RO("ro", "Română"),
    RU("ru", "Русский"),
    EN("en", "English");

    public static final String COOKIE = "ttm-lang";

    private final String code;
    private final String nativeName;

    Language(String code, String nativeName) {
        this.code = code;
        this.nativeName = nativeName;
    }

    public String code() {
        return code;
    }

    public String nativeName() {
        return nativeName;
    }

    public Locale locale() {
        return Locale.forLanguageTag(code);
    }

    /** Limba după cod („ro”, „ru”, „en”); orice altceva = română. */
    public static Language fromCode(String code) {
        if (code != null) {
            for (Language language : values()) {
                if (language.code.equalsIgnoreCase(code.trim())) {
                    return language;
                }
            }
        }
        return RO;
    }

    /** Limba unui {@link Locale}, sau {@code null} dacă nu e una dintre limbile aplicației. */
    public static Language ofLocaleOrNull(Locale locale) {
        if (locale == null) {
            return null;
        }
        for (Language language : values()) {
            if (language.code.equalsIgnoreCase(locale.getLanguage())) {
                return language;
            }
        }
        return null;
    }

    public static Language of(Locale locale) {
        Language language = ofLocaleOrNull(locale);
        return language != null ? language : RO;
    }
}
