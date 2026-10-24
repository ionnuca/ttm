package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import md.ttm.i18n.Tr;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.service.tournament.GroupView;
import md.ttm.ui.components.Notifications;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.IntStream;

/**
 * „Începe etapa 2” la „Grupe + eliminare”: câți jucători din fiecare grupă intră în tabloul eliminatoriu
 * și tipul tabloului (olimpic sau cu ramura de jos).
 */
class StartKnockoutDialog extends Dialog {

    private final List<GroupView> groups;
    private final Select<Integer> qualifiers = new Select<>();
    private final RadioButtonGroup<KnockoutType> type = new RadioButtonGroup<>(Tr.t("Tabloul eliminatoriu"));
    private final Div preview = new Div();

    StartKnockoutDialog(List<GroupView> groups, BiConsumer<Integer, KnockoutType> onStart) {
        this.groups = groups;
        setHeaderTitle(Tr.t("Începe etapa 2"));
        setWidth("min(95vw, 36rem)");

        int smallest = groups.stream().mapToInt(g -> g.members().size()).min().orElse(2);
        qualifiers.setLabel(Tr.t("Calificați din fiecare grupă în tabloul eliminatoriu"));
        qualifiers.setItems(IntStream.rangeClosed(1, smallest).boxed().toList());
        qualifiers.setItemLabelGenerator(n -> n == 1 ? Tr.t("Primul din fiecare grupă") : Tr.t("Primii {0} din fiecare grupă", n));
        qualifiers.setValue(groups.size() == 1 ? Math.min(4, smallest) : Math.min(2, smallest));
        qualifiers.setWidthFull();
        qualifiers.addValueChangeListener(e -> updatePreview());

        type.setItems(KnockoutType.values());
        type.setItemLabelGenerator(KnockoutType::getLabel);
        type.addThemeName("vertical");
        type.setValue(KnockoutType.SINGLE);
        type.addValueChangeListener(e -> updatePreview());

        preview.getStyle().set("font-size", "var(--lumo-font-size-s)").set("color", "var(--lumo-secondary-text-color)");
        add(qualifiers, type, preview);
        updatePreview();

        Button start = new Button(Tr.t("Începe etapa 2"), VaadinIcon.PLAY.create(), e -> {
            try {
                onStart.accept(qualifiers.getValue(), type.getValue());
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        getFooter().add(new Button(Tr.t("Anulează"), e -> close()), start);
    }

    private void updatePreview() {
        Integer q = qualifiers.getValue();
        if (q == null || type.getValue() == null) {
            preview.setText("");
            return;
        }
        long count = groups.stream()
                .flatMap(g -> g.standings().stream())
                .filter(row -> row.place() > 0 && row.place() <= q)
                .count();
        int size = 1;
        while (size < count) {
            size *= 2;
        }
        long byes = size - count;
        String text = Tr.t("În tablou intră {0} jucători, așezați după locul din grupă (întâi câștigătorii de grupă), apoi după rating.", count);
        if (byes > 0) {
            text += " " + Tr.t("Tabloul are {0} locuri: cei mai bine clasați {1} trec direct în turul 2.", size, byes);
        }
        text += " " + (type.getValue() == KnockoutType.SINGLE
                ? Tr.t("Cine pierde iese din turneu; cei învinși în semifinale împart locul 3.")
                : Tr.t("Cine pierde în tabloul principal trece în ramura de jos; cine pierde și acolo iese. Câștigătorul ramurii de jos joacă marea finală; dacă o câștigă, se joacă un meci decisiv."));
        preview.setText(text);
    }
}
