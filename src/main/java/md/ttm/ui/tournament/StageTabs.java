package md.ttm.ui.tournament;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Html;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.NativeButton;
import com.vaadin.flow.component.html.Span;
import md.ttm.i18n.Tr;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * Etapele unui turneu „Grupe + finale” / „Grupe + eliminare” pe taburi: un comutator Grupe | Finale
 * și, în fiecare etapă, câte un cip pentru fiecare grupă (sau finală, tablou) plus „Toate” (implicit).
 * Comutarea doar ascunde și arată secțiunile deja construite, fără reîncărcarea paginii.
 */
final class StageTabs extends Div {

    /** O grupă / finală / un tablou: eticheta cipului și conținutul. */
    record Part(String label, Component content) {
    }

    /**
     * @param key      cheia din adresă ({@code ?etapa=})
     * @param progress textul mic de lângă etichetă (de ex. {@code 5/12}); {@code null} = fără
     * @param top      componentele de deasupra cipurilor, arătate mereu în această etapă
     */
    record Stage(String key, String label, String progress, List<Component> top, List<Part> parts) {
    }

    static final String ALL = "toate";

    private static final String STYLE = """
            <style>
            .ttm-stage-switch{display:flex;width:100%;max-width:28rem;box-sizing:border-box;padding:3px;gap:3px;
              border-radius:var(--lumo-border-radius-m);background:var(--lumo-contrast-5pct);margin-top:var(--lumo-space-m)}
            .ttm-stage-switch button{flex:1;min-width:0;border:1px solid transparent;background:none;cursor:pointer;
              font:inherit;font-size:var(--lumo-font-size-m);color:var(--lumo-secondary-text-color);
              padding:var(--lumo-space-xs) var(--lumo-space-s);border-radius:calc(var(--lumo-border-radius-m) - 2px);
              white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
            .ttm-stage-switch button[aria-pressed=true]{background:var(--lumo-base-color);color:var(--lumo-body-text-color);
              border-color:var(--lumo-contrast-10pct);font-weight:600}
            .ttm-stage-switch .ttm-stage-progress{font-size:var(--lumo-font-size-xs);font-weight:400;
              color:var(--lumo-secondary-text-color);margin-left:var(--lumo-space-xs)}
            .ttm-stage-chips{display:flex;gap:var(--lumo-space-xs);overflow-x:auto;width:100%;
              padding:var(--lumo-space-s) 0 var(--lumo-space-xs);scrollbar-width:thin}
            .ttm-stage-chips button{flex:none;border:1px solid var(--lumo-contrast-20pct);background:none;cursor:pointer;
              font:inherit;font-size:var(--lumo-font-size-s);color:var(--lumo-body-text-color);
              padding:2px var(--lumo-space-m);border-radius:999px;white-space:nowrap}
            .ttm-stage-chips button[aria-pressed=true]{background:var(--lumo-primary-color-10pct);
              border-color:var(--lumo-primary-color-50pct);color:var(--lumo-primary-text-color);font-weight:600}
            .ttm-stage-pane{display:flex;flex-direction:column;align-items:stretch;gap:var(--lumo-space-s);width:100%}
            .ttm-stage-pane[hidden]{display:none}
            </style>
            """;

    private final List<Stage> stages;
    private final List<NativeButton> stageButtons = new ArrayList<>();
    private final List<Div> panes = new ArrayList<>();
    private final List<List<NativeButton>> chips = new ArrayList<>();
    private final BiConsumer<String, String> onChange;
    private int stage;

    /**
     * @param selectedStage cheia etapei cerute; {@code null} sau necunoscută = {@code defaultStage}
     * @param selectedPart  cipul cerut (număr de la 1 sau {@link #ALL}); {@code null} = „Toate”
     * @param onChange      după o alegere a utilizatorului: cheia etapei și cipul ales ({@code null} = automat)
     */
    StageTabs(List<Stage> stages, int defaultStage, String selectedStage, String selectedPart,
              BiConsumer<String, String> onChange) {
        this.stages = stages;
        this.onChange = onChange;
        setWidthFull();
        add(new Html(STYLE.strip()));

        Div switcher = new Div();
        switcher.addClassName("ttm-stage-switch");
        switcher.getElement().setAttribute("role", "group");
        add(switcher);
        for (int i = 0; i < stages.size(); i++) {
            Stage s = stages.get(i);
            NativeButton button = new NativeButton();
            button.add(new Span(s.label()));
            if (s.progress() != null) {
                Span progress = new Span(s.progress());
                progress.addClassName("ttm-stage-progress");
                button.add(progress);
            }
            int index = i;
            button.addClickListener(e -> {
                if (stage != index) {
                    show(index, null);
                    onChange.accept(stages.get(index).key(), null);
                }
            });
            stageButtons.add(button);
            switcher.add(button);

            Div pane = new Div();
            pane.addClassName("ttm-stage-pane");
            s.top().forEach(pane::add);
            List<NativeButton> paneChips = new ArrayList<>();
            if (s.parts().size() > 1) {
                Div chipRow = new Div();
                chipRow.addClassName("ttm-stage-chips");
                chipRow.add(chip(Tr.t("Toate"), index, ALL, paneChips));
                for (int p = 0; p < s.parts().size(); p++) {
                    chipRow.add(chip(s.parts().get(p).label(), index, String.valueOf(p + 1), paneChips));
                }
                pane.add(chipRow);
            }
            s.parts().forEach(part -> pane.add(part.content()));
            chips.add(paneChips);
            panes.add(pane);
            add(pane);
        }

        int requested = defaultStage;
        for (int i = 0; i < stages.size(); i++) {
            if (stages.get(i).key().equals(selectedStage)) {
                requested = i;
            }
        }
        show(requested, selectedPart);
    }

    private NativeButton chip(String label, int stageIndex, String key, List<NativeButton> paneChips) {
        NativeButton chip = new NativeButton(label);
        chip.getElement().setAttribute("data-part", key);
        chip.addClickListener(e -> {
            showPart(stageIndex, key);
            onChange.accept(stages.get(stageIndex).key(), key);
        });
        paneChips.add(chip);
        return chip;
    }

    private void show(int index, String part) {
        stage = index;
        for (int i = 0; i < stages.size(); i++) {
            boolean selected = i == index;
            stageButtons.get(i).getElement().setAttribute("aria-pressed", String.valueOf(selected));
            panes.get(i).setVisible(selected);
        }
        showPart(index, resolvePart(stages.get(index), part));
    }

    /** Cipul cerut, dacă există; altfel „Toate”. */
    private static String resolvePart(Stage s, String part) {
        if (part != null && !ALL.equals(part)) {
            try {
                int n = Integer.parseInt(part);
                if (n >= 1 && n <= s.parts().size()) {
                    return part;
                }
            } catch (NumberFormatException ignored) {
                // cip necunoscut: se arată toate
            }
        }
        return ALL;
    }

    private void showPart(int stageIndex, String key) {
        Stage s = stages.get(stageIndex);
        if (s.parts().size() <= 1) {
            return;
        }
        for (int p = 0; p < s.parts().size(); p++) {
            s.parts().get(p).content().setVisible(ALL.equals(key) || key.equals(String.valueOf(p + 1)));
        }
        for (NativeButton chip : chips.get(stageIndex)) {
            chip.getElement().setAttribute("aria-pressed",
                    String.valueOf(key.equals(chip.getElement().getAttribute("data-part"))));
        }
    }
}
