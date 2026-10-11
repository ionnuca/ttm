package md.ttm.i18n;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.server.VaadinSession;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Traducerea textelor interfeței. Textul în română este cheia (ca în gettext): {@code Tr.t("Salvează")}.
 * Traducerile stau în {@code i18n/ru.json} și {@code i18n/en.json}; un text fără traducere rămâne în română.
 * <p>
 * Parametrii se scriu {@code {0}}, {@code {1}} …: {@code Tr.t("Meciuri jucate: {0} din {1}", 3, 10)}.
 */
public final class Tr {

    private static final Map<Language, Map<String, String>> DICTIONARIES = load();

    private Tr() {
    }

    /**
     * Marchează un text de tradus care se traduce mai târziu (de exemplu etichetele enum-urilor);
     * întoarce textul neschimbat.
     */
    public static String key(String ro) {
        return ro;
    }

    /** Textul în limba interfeței curente. */
    public static String t(String ro, Object... args) {
        return t(current(), ro, args);
    }

    public static String t(Language language, String ro, Object... args) {
        String text = ro;
        if (language != Language.RO) {
            String translated = DICTIONARIES.get(language).get(ro);
            if (translated != null && !translated.isBlank()) {
                text = translated;
            }
        }
        return format(text, args);
    }

    /** Limba interfeței curente (a UI-ului sau a sesiunii); română dacă nu există niciuna. */
    public static Language current() {
        UI ui = UI.getCurrent();
        Locale locale = ui != null ? ui.getLocale() : null;
        if (locale == null && VaadinSession.getCurrent() != null) {
            locale = VaadinSession.getCurrent().getLocale();
        }
        return Language.of(locale);
    }

    public static Locale locale() {
        return current().locale();
    }

    /** Înlocuiește {0}, {1} … cu argumentele (fără reguli speciale pentru apostrofuri). */
    public static String format(String text, Object... args) {
        if (args == null || args.length == 0) {
            return text;
        }
        String result = text;
        for (int i = 0; i < args.length; i++) {
            result = result.replace("{" + i + "}", String.valueOf(args[i]));
        }
        return result;
    }

    /** Dicționarul unei limbi (pentru teste). */
    public static Map<String, String> dictionary(Language language) {
        return DICTIONARIES.get(language);
    }

    private static Map<Language, Map<String, String>> load() {
        Map<Language, Map<String, String>> dictionaries = new EnumMap<>(Language.class);
        ObjectMapper mapper = new ObjectMapper();
        for (Language language : Language.values()) {
            if (language == Language.RO) {
                dictionaries.put(language, Map.of());
                continue;
            }
            try (InputStream in = Tr.class.getResourceAsStream("/i18n/" + language.code() + ".json")) {
                dictionaries.put(language, in == null ? Map.of()
                        : Map.copyOf(mapper.readValue(in, new TypeReference<Map<String, String>>() {
                })));
            } catch (IOException e) {
                throw new UncheckedIOException("Dicționarul " + language.code() + " nu poate fi citit", e);
            }
        }
        return dictionaries;
    }
}
