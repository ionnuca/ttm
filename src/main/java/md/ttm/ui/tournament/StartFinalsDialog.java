package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.CheckboxGroup;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.FinalsSettings;
import md.ttm.service.tournament.GroupView;
import md.ttm.ui.components.Notifications;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * „Începe etapa 2”: organizatorul (administrator sau manager de turnee) alege câte finale se joacă și câți jucători
 * din fiecare grupă trec în fiecare finală; ultima finală îi primește pe toți cei rămași. La un turneu comercial
 * alege și finalele al căror câștigător primește cât taxa de participare. Dialogul arată pe loc cine unde joacă.
 */
class StartFinalsDialog extends Dialog {

    private final List<GroupView> groups;
    private final List<Integer> sizes;
    private final Select<Integer> finalCount = new Select<>();
    private final Select<Integer> qualifiers = new Select<>();
    private final CheckboxGroup<Integer> feeRefunds = new CheckboxGroup<>();
    private final Div preview = new Div();

    StartFinalsDialog(List<GroupView> groups, boolean commercial, Consumer<FinalsSettings> onStart) {
        this.groups = groups;
        setHeaderTitle(Tr.t("Începe etapa 2"));
        setWidth("min(95vw, 36rem)");

        sizes = groups.stream().map(g -> g.members().size()).toList();
        int largest = sizes.stream().mapToInt(Integer::intValue).max().orElse(2);
        finalCount.setLabel(Tr.t("Număr de finale"));
        List<Integer> counts = IntStream.rangeClosed(2, Math.max(2, largest)).boxed()
                .filter(n -> FinalsSettings.maxQualifiers(n, sizes) >= 1).toList();
        finalCount.setItems(counts.isEmpty() ? List.of(2) : counts);
        finalCount.setValue(2);
        finalCount.setHelperText(Tr.t("Ultima finală îi primește pe toți jucătorii rămași"));
        finalCount.setWidthFull();
        finalCount.addValueChangeListener(e -> updateFinalCount());

        qualifiers.setWidthFull();
        qualifiers.addValueChangeListener(e -> updatePreview());

        feeRefunds.setLabel(Tr.t("Câștigătorul primește cât taxa de participare"));
        feeRefunds.setItemLabelGenerator(n -> "F" + n);
        feeRefunds.setHelperText(Tr.t("Restul sumei se împarte între premiații Finalei 1"));
        feeRefunds.setVisible(commercial);

        Paragraph note = new Paragraph(Tr.t("Toate finalele se joacă Round Robin. Jucătorii care s-au întâlnit deja în aceeași grupă nu mai joacă între ei: rezultatul din etapa 1 se preia în tabelul finalei."));
        note.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        preview.getStyle().set("font-size", "var(--lumo-font-size-s)");
        add(finalCount, qualifiers, feeRefunds, preview, note);
        updateFinalCount();
        feeRefunds.setValue(Set.of(2)); // ca până acum: câștigătorul Finalei 2 primește taxa

        Button start = new Button(Tr.t("Începe etapa 2"), VaadinIcon.PLAY.create(), e -> {
            try {
                onStart.accept(settings());
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        getFooter().add(new Button(Tr.t("Anulează"), e -> close()), start);
    }

    private FinalsSettings settings() {
        return new FinalsSettings(finalCount.getValue(), qualifiers.getValue(),
                feeRefunds.isVisible() ? feeRefunds.getValue() : Set.of());
    }

    /** Alt număr de finale: se schimbă câți pot trece în fiecare finală și finalele care pot returna taxa. */
    private void updateFinalCount() {
        int count = finalCount.getValue();
        int min = FinalsSettings.minQualifiers(sizes.size());
        int max = Math.max(min, FinalsSettings.maxQualifiers(count, sizes));
        Integer previous = qualifiers.getValue();
        qualifiers.setLabel(count == 2
                ? Tr.t("Calificați din fiecare grupă în F1")
                : Tr.t("Jucători din fiecare grupă în fiecare finală (în afară de ultima)"));
        qualifiers.setItems(IntStream.rangeClosed(min, max).boxed().toList());
        qualifiers.setItemLabelGenerator(n -> count == 2
                ? (n == 1 ? Tr.t("Primul din fiecare grupă") : Tr.t("Primii {0} din fiecare grupă", n))
                : (n == 1 ? Tr.t("Câte un jucător din fiecare grupă") : Tr.t("Câte {0} jucători din fiecare grupă", n)));
        qualifiers.setValue(previous != null && previous >= min && previous <= max ? previous
                : Math.max(min, Math.min(count == 2 ? 2 : 1, max)));

        Set<Integer> refunds = new HashSet<>(feeRefunds.getValue());
        List<Integer> candidates = IntStream.rangeClosed(2, count).boxed().toList();
        refunds.retainAll(candidates);
        feeRefunds.setItems(candidates);
        feeRefunds.setValue(refunds);
        updatePreview();
    }

    private void updatePreview() {
        preview.removeAll();
        Integer count = finalCount.getValue();
        Integer q = qualifiers.getValue();
        if (count == null || q == null) {
            return;
        }
        FinalsSettings settings = new FinalsSettings(count, q, Set.of());
        List<List<String>> finals = new ArrayList<>();
        for (int k = 0; k < count; k++) {
            finals.add(new ArrayList<>());
        }
        for (GroupView group : groups) {
            for (int i = 0; i < group.members().size(); i++) {
                TournamentParticipant p = group.members().get(i);
                finals.get(settings.finalFor(group.standings().get(i).place()) - 1).add(p.getPlayer().getDisplayName());
            }
        }
        for (int k = 0; k < count; k++) {
            preview.add(line(Tr.t("F{0} ({1} jucători): ", k + 1, finals.get(k).size()), finals.get(k)));
        }
    }

    private static Div line(String title, List<String> names) {
        Div div = new Div();
        div.getStyle().set("margin", "var(--lumo-space-xs) 0");
        Span label = new Span(title);
        label.getStyle().set("font-weight", "600");
        div.add(label, new Span(names.stream().collect(Collectors.joining(", "))));
        return div;
    }
}
