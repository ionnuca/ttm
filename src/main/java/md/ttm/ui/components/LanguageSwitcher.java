package md.ttm.ui.components;

import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.select.SelectVariant;
import md.ttm.i18n.Language;
import md.ttm.i18n.Tr;
import md.ttm.security.SecurityUtils;
import md.ttm.service.user.UserService;

/**
 * Comutatorul de limbă din bara de sus (RO / RU / EN). Pentru utilizatorul autentificat limba aleasă
 * devine limba contului; pentru vizitatori se ține minte în browser (cookie). Pagina se reîncarcă în noua limbă.
 */
public class LanguageSwitcher extends Select<Language> {

    public LanguageSwitcher(UserService userService) {
        setItems(Language.values());
        setItemLabelGenerator(language -> language.code().toUpperCase());
        setValue(Tr.current());
        setAriaLabel(Tr.t("Limba"));
        setTooltipText(Tr.t("Limba"));
        addThemeVariants(SelectVariant.LUMO_SMALL);
        setWidth("4.75rem");
        addValueChangeListener(e -> {
            if (!e.isFromClient() || e.getValue() == null) {
                return;
            }
            apply(e.getValue(), userService);
        });
    }

    /** Salvează limba (în cont și în browser) și reîncarcă pagina. */
    public static void apply(Language language, UserService userService) {
        if (SecurityUtils.isAuthenticated()) {
            userService.updateOwnLanguage(language);
        }
        getCurrentUi().getPage().executeJs(
                "document.cookie = $0 + '=' + $1 + '; path=/; max-age=31536000; samesite=lax'; location.reload();",
                Language.COOKIE, language.code());
    }

    private static com.vaadin.flow.component.UI getCurrentUi() {
        return com.vaadin.flow.component.UI.getCurrent();
    }
}
