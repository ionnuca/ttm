package md.ttm.i18n;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifică dicționarele: fiecare text de tradus din cod ({@code Tr.t("…")}, {@code Tr.key("…")},
 * mesajele {@code BusinessException} și cele din adnotările de validare) are traducere în rusă și engleză,
 * cu aceiași parametri.
 */
class TranslationsTest {

    private static final String STRING = "\"((?:[^\"\\\\\\n]|\\\\.)*)\"";
    private static final Pattern[] PATTERNS = {
            Pattern.compile("Tr\\.t\\(\\s*(?:\\w+(?:\\.\\w+)?\\s*,\\s*(?=\"))?" + STRING),
            Pattern.compile("Tr\\.key\\(\\s*" + STRING),
            Pattern.compile("new BusinessException\\(\\s*" + STRING),
            Pattern.compile("message\\s*=\\s*" + STRING),
            Pattern.compile("requireName\\([^,()]+,\\s*" + STRING),
    };
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\w+}");

    @Test
    void toateTextele_auTraducereInRusaSiEngleza() throws IOException {
        Set<String> keys = keysInSource();
        assertThat(keys).hasSizeGreaterThan(300);
        for (Language language : Set.of(Language.RU, Language.EN)) {
            Set<String> missing = new TreeSet<>(keys);
            missing.removeAll(Tr.dictionary(language).keySet());
            assertThat(missing).as("Texte fără traducere în %s", language).isEmpty();
        }
    }

    @Test
    void traducerile_pastreazaParametrii() {
        for (Language language : Set.of(Language.RU, Language.EN)) {
            Tr.dictionary(language).forEach((ro, translated) ->
                    assertThat(placeholders(translated)).as("%s: %s", language, ro).isEqualTo(placeholders(ro)));
        }
    }

    @Test
    void textulNetradus_ramaneInRomana() {
        assertThat(Tr.t(Language.RU, "Salvează")).isEqualTo("Сохранить");
        assertThat(Tr.t(Language.EN, "Meciuri jucate: {0} din {1}", 3, 10)).isEqualTo("Matches played: 3 of 10");
        assertThat(Tr.t(Language.EN, "Text care nu există în dicționar")).isEqualTo("Text care nu există în dicționar");
        assertThat(Tr.t(Language.RO, "Salvează")).isEqualTo("Salvează");
    }

    @Test
    void ghidulExistaInFiecareLimba() {
        for (Language language : Language.values()) {
            assertThat(TranslationsTest.class.getResource("/help/" + language.code() + ".html"))
                    .as("Ghidul în %s", language).isNotNull();
        }
    }

    private static Set<String> keysInSource() throws IOException {
        Set<String> keys = new TreeSet<>();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(f -> f.toString().endsWith(".java")).toList()) {
                String source = Files.readString(file);
                for (Pattern pattern : PATTERNS) {
                    Matcher m = pattern.matcher(source);
                    while (m.find()) {
                        keys.add(m.group(1).replace("\\\"", "\""));
                    }
                }
            }
        }
        return keys;
    }

    private static Set<String> placeholders(String text) {
        Set<String> result = new TreeSet<>();
        Matcher m = PLACEHOLDER.matcher(text);
        while (m.find()) {
            result.add(m.group());
        }
        return result;
    }
}
