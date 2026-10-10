package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.select.Select;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.GroupView;
import md.ttm.ui.components.Notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * „Începe etapa 2”: administratorul alege câți jucători din fiecare grupă trec în Finala 1.
 * Dialogul arată pe loc cine se califică.
 */
class StartFinalsDialog extends Dialog {

    private final List<GroupView> groups;
    private final Select<Integer> qualifiers = new Select<>();
    private final Div preview = new Div();

    StartFinalsDialog(List<GroupView> groups, Consumer<Integer> onStart) {
        this.groups = groups;
        setHeaderTitle("Începe etapa 2");
        setWidth("min(95vw, 36rem)");

        int smallest = groups.stream().mapToInt(g -> g.members().size()).min().orElse(2);
        qualifiers.setLabel("Calificați din fiecare grupă în Finala 1");
        qualifiers.setItems(IntStream.range(1, smallest).boxed().toList());
        qualifiers.setItemLabelGenerator(n -> n == 1 ? "Primul din fiecare grupă" : "Primii " + n + " din fiecare grupă");
        qualifiers.setValue(Math.min(2, smallest - 1));
        qualifiers.setWidthFull();
        qualifiers.addValueChangeListener(e -> updatePreview());

        Paragraph note = new Paragraph("Ambele finale se joacă Round Robin. Jucătorii care s-au întâlnit deja în "
                + "aceeași grupă nu mai joacă între ei: rezultatul din etapa 1 se preia în tabelul finalei.");
        note.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        preview.getStyle().set("font-size", "var(--lumo-font-size-s)");
        add(qualifiers, preview, note);
        updatePreview();

        Button start = new Button("Începe etapa 2", VaadinIcon.PLAY.create(), e -> {
            try {
                onStart.accept(qualifiers.getValue());
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        getFooter().add(new Button("Anulează", e -> close()), start);
    }

    private void updatePreview() {
        preview.removeAll();
        Integer q = qualifiers.getValue();
        if (q == null) {
            return;
        }
        List<String> final1 = new ArrayList<>();
        List<String> final2 = new ArrayList<>();
        for (GroupView group : groups) {
            for (int i = 0; i < group.members().size(); i++) {
                TournamentParticipant p = group.members().get(i);
                String name = p.getPlayer().getDisplayName();
                (group.standings().get(i).place() <= q ? final1 : final2).add(name);
            }
        }
        preview.add(line("Finala 1 (" + final1.size() + " jucători): ", final1),
                line("Finala 2 (" + final2.size() + " jucători): ", final2));
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
