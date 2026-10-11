package md.ttm.i18n;

import com.vaadin.flow.server.ServiceInitEvent;
import com.vaadin.flow.server.VaadinRequest;
import com.vaadin.flow.server.VaadinServiceInitListener;
import jakarta.servlet.http.Cookie;
import md.ttm.model.user.AppUser;
import md.ttm.repository.AppUserRepository;
import md.ttm.security.SecurityUtils;
import org.springframework.stereotype.Component;


/**
 * Alege limba fiecărei pagini noi: limba salvată în contul utilizatorului; pentru vizitatori,
 * limba aleasă anterior în acest browser (cookie); altfel româna.
 */
@Component
public class LanguageInitializer implements VaadinServiceInitListener {

    private final AppUserRepository userRepository;

    public LanguageInitializer(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    public void serviceInit(ServiceInitEvent event) {
        event.getSource().addUIInitListener(e -> {
            Language language = resolve(VaadinRequest.getCurrent());
            e.getUI().setLocale(language.locale());
            e.getUI().getSession().setLocale(language.locale());
        });
    }

    Language resolve(VaadinRequest request) {
        Language account = SecurityUtils.currentUsername()
                .flatMap(userRepository::findByUsername)
                .map(AppUser::getLanguage)
                .orElse(null);
        if (account != null) {
            return account;
        }
        if (request != null) {
            if (request.getCookies() != null) {
                for (Cookie cookie : request.getCookies()) {
                    if (Language.COOKIE.equals(cookie.getName())) {
                        return Language.fromCode(cookie.getValue());
                    }
                }
            }
        }
        return Language.RO;
    }
}
