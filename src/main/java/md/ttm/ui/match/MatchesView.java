package md.ttm.ui.match;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.HasDynamicTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.router.RouteAlias;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.service.match.MatchService;
import md.ttm.service.match.MatchSummary;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.ui.layout.MainLayout;

import java.util.List;
import java.util.Map;
import java.util.Locale;

/** Ultimele meciuri cu rezultat, din toate turneele, cu căutare după jucător sau turneu. */
@Route(value = "", layout = MainLayout.class)
@RouteAlias(value = "meciuri", layout = MainLayout.class)
@AnonymousAllowed
public class MatchesView extends VerticalLayout implements HasDynamicTitle {

    @Override
    public String getPageTitle() {
        return Tr.t("Meciuri") + " | TTM";
    }


    static final int LIMIT = 200;

    private final List<MatchSummary> matches;
    private final TextField search = new TextField();
    private final VerticalLayout results = new VerticalLayout();
    private final Span counter = new Span();

    private final Map<Long, Long> photoVersions;

    public MatchesView(MatchService matchService, PlayerPhotoService photoService) {
        setMaxWidth("80rem");
        matches = matchService.findRecent(LIMIT);
        photoVersions = photoService.versions();

        H2 title = new H2(Tr.t("Meciuri"));
        title.getStyle().set("margin", "0");
        counter.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        search.setPlaceholder(Tr.t("Caută după jucător sau turneu"));
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();
        search.setMaxWidth("24rem");
        search.addValueChangeListener(e -> show());

        results.setPadding(false);
        results.setWidthFull();
        results.setSpacing(false);
        add(header, search, results);
        show();
    }

    private void show() {
        String term = search.getValue() == null ? "" : search.getValue().trim().toLowerCase(Locale.ROOT);
        List<MatchSummary> shown = term.isEmpty() ? matches : matches.stream()
                .filter(m -> contains(m.playerA().getDisplayName(), term)
                        || contains(m.playerB().getDisplayName(), term)
                        || contains(m.tournamentName(), term))
                .toList();
        counter.setText(matches.isEmpty() ? "" : term.isEmpty()
                ? Tr.t("ultimele {0}", matches.size())
                : Tr.t("{0} din ultimele {1}", shown.size(), matches.size()));
        results.removeAll();
        if (shown.isEmpty()) {
            results.add(new Paragraph(matches.isEmpty()
                    ? Tr.t("Încă nu s-a jucat niciun meci.")
                    : Tr.t("Niciun meci nu corespunde căutării.")));
        } else {
            results.add(new MatchList(shown, null, photoVersions));
        }
    }

    private static boolean contains(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term);
    }
}
