package md.ttm.ui.match;

import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.value.ValueChangeMode;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.auth.AnonymousAllowed;
import md.ttm.service.match.MatchService;
import md.ttm.service.match.MatchSummary;
import md.ttm.ui.layout.MainLayout;

import java.util.List;
import java.util.Locale;

/** Ultimele meciuri cu rezultat, din toate turneele, cu căutare după jucător sau turneu. */
@Route(value = "meciuri", layout = MainLayout.class)
@PageTitle("Meciuri | TTM")
@AnonymousAllowed
public class MatchesView extends VerticalLayout {

    static final int LIMIT = 200;

    private final List<MatchSummary> matches;
    private final TextField search = new TextField();
    private final VerticalLayout results = new VerticalLayout();
    private final Span counter = new Span();

    public MatchesView(MatchService matchService) {
        setMaxWidth("52rem");
        matches = matchService.findRecent(LIMIT);

        H2 title = new H2("Meciuri");
        title.getStyle().set("margin", "0");
        counter.getStyle().set("color", "var(--lumo-secondary-text-color)");
        HorizontalLayout header = new HorizontalLayout(title, counter);
        header.setAlignItems(FlexComponent.Alignment.BASELINE);

        search.setPlaceholder("Caută după jucător sau turneu");
        search.setPrefixComponent(VaadinIcon.SEARCH.create());
        search.setClearButtonVisible(true);
        search.setValueChangeMode(ValueChangeMode.LAZY);
        search.setWidthFull();
        search.setMaxWidth("24rem");
        search.addValueChangeListener(e -> show());

        results.setPadding(false);
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
                ? "ultimele " + matches.size()
                : shown.size() + " din ultimele " + matches.size());
        results.removeAll();
        if (shown.isEmpty()) {
            results.add(new Paragraph(matches.isEmpty()
                    ? "Încă nu s-a jucat niciun meci."
                    : "Niciun meci nu corespunde căutării."));
        } else {
            results.add(new MatchList(shown, null));
        }
    }

    private static boolean contains(String text, String term) {
        return text != null && text.toLowerCase(Locale.ROOT).contains(term);
    }
}
