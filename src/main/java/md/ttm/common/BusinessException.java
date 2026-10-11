package md.ttm.common;

import md.ttm.i18n.Tr;

/**
 * Eroare de business cu mesaj destinat direct utilizatorului (în română).
 */
public class BusinessException extends RuntimeException {

    private final String template;
    private final transient Object[] args;

    /**
     * @param template mesajul în română (cheia traducerii), cu {0}, {1} … pentru valori
     * @param args     valorile din mesaj
     */
    public BusinessException(String template, Object... args) {
        super(Tr.format(template, args));
        this.template = template;
        this.args = args;
    }

    /** Mesajul în limba interfeței curente. */
    @Override
    public String getLocalizedMessage() {
        return Tr.t(template, args);
    }
}
