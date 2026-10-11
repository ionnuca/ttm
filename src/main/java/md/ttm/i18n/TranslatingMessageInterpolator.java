package md.ttm.i18n;

import jakarta.validation.MessageInterpolator;
import jakarta.validation.Validation;

import java.util.Locale;

/**
 * Traduce mesajele de validare din adnotări ({@code @NotBlank(message = "Introduceți numele")}):
 * mesajul românesc e cheia din dicționar, ca la {@link Tr}. Configurat în META-INF/validation.xml,
 * deci îl folosesc și formularele Vaadin (cu limba interfeței). Se traduce șablonul, apoi se completează
 * parametrii adnotării: {@code "Parola trebuie să aibă cel puțin {min} caractere"}.
 */
public class TranslatingMessageInterpolator implements MessageInterpolator {

    private final MessageInterpolator delegate =
            Validation.byDefaultProvider().configure().getDefaultMessageInterpolator();

    @Override
    public String interpolate(String messageTemplate, Context context) {
        return delegate.interpolate(Tr.t(Tr.current(), messageTemplate), context);
    }

    @Override
    public String interpolate(String messageTemplate, Context context, Locale locale) {
        Language language = Language.ofLocaleOrNull(locale);
        return delegate.interpolate(Tr.t(language != null ? language : Tr.current(), messageTemplate), context, locale);
    }
}
