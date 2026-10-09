package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import md.ttm.model.tournament.MatchOutcome;
import md.ttm.model.tournament.TournamentMatch;
import md.ttm.model.tournament.TournamentParticipant;
import md.ttm.service.tournament.MatchResultForm;
import md.ttm.ui.components.Notifications;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.IntStream;

/**
 * Introducerea rezultatului unui meci: scorul la seturi sau victoria tehnică (W),
 * când adversarul refuză jocul.
 */
class MatchResultDialog extends Dialog {

    private final RadioButtonGroup<MatchOutcome> outcome = new RadioButtonGroup<>("Rezultat");
    private final Select<Integer> setsA = new Select<>();
    private final Select<Integer> setsB = new Select<>();
    private final RadioButtonGroup<TournamentParticipant> walkoverWinner = new RadioButtonGroup<>("Câștigă tehnic");
    private final Span error = new Span();
    private final int setsToWin;

    /**
     * @param onClear apelat la „Șterge rezultatul”; {@code null} ascunde butonul
     */
    MatchResultDialog(TournamentMatch match, int setsToWin, Consumer<MatchResultForm> onSave, Runnable onClear) {
        this.setsToWin = setsToWin;
        TournamentParticipant a = match.getParticipantA();
        TournamentParticipant b = match.getParticipantB();
        setHeaderTitle("Rezultatul meciului");
        setWidth("min(95vw, 32rem)");

        Span subtitle = new Span("Turul " + match.getRoundNo() + " · best of " + (setsToWin * 2 - 1)
                + " (câștigă primul la " + setsToWin + " seturi)");
        subtitle.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        outcome.setItems(MatchOutcome.values());
        outcome.setItemLabelGenerator(o -> o == MatchOutcome.NORMAL ? "Pe seturi" : "Victorie tehnică (W)");

        // --- scor pe seturi ---
        List<Integer> options = IntStream.rangeClosed(0, setsToWin).boxed().toList();
        setsA.setItems(options);
        setsB.setItems(options);
        setsA.setWidth("5rem");
        setsB.setWidth("5rem");
        setsA.setAriaLabel("Seturi " + a.getPlayer().getDisplayName());
        setsB.setAriaLabel("Seturi " + b.getPlayer().getDisplayName());
        HorizontalLayout score = new HorizontalLayout(playerName(a, true), setsA, new Span(":"), setsB, playerName(b, false));
        score.setAlignItems(FlexComponent.Alignment.CENTER);
        score.setWidthFull();
        score.setSpacing(true);

        HorizontalLayout quick = new HorizontalLayout();
        quick.getStyle().set("flex-wrap", "wrap").set("gap", "var(--lumo-space-xs)");
        for (String preset : presets(setsToWin)) {
            Button button = new Button(preset, e -> {
                String[] parts = preset.split(":");
                setsA.setValue(Integer.parseInt(parts[0]));
                setsB.setValue(Integer.parseInt(parts[1]));
                error.setText("");
            });
            button.addThemeVariants(ButtonVariant.LUMO_SMALL, ButtonVariant.LUMO_CONTRAST);
            quick.add(button);
        }
        VerticalLayout setsPanel = new VerticalLayout(score, quick);
        setsPanel.setPadding(false);

        // --- victorie tehnică ---
        walkoverWinner.setItems(a, b);
        walkoverWinner.setItemLabelGenerator(p -> p.getPlayer().getDisplayName());
        Paragraph walkoverNote = new Paragraph("Adversarul a refuzat jocul. Câștigătorul primește 2 puncte, "
                + "celălalt 0; la seturi se socotește " + setsToWin + ":0.");
        walkoverNote.getStyle()
                .set("color", "var(--lumo-secondary-text-color)")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("margin", "0");
        VerticalLayout walkoverPanel = new VerticalLayout(walkoverWinner, walkoverNote);
        walkoverPanel.setPadding(false);

        // valorile existente
        if (match.isPlayed() && match.isWalkover()) {
            outcome.setValue(MatchOutcome.WALKOVER);
            walkoverWinner.setValue(match.getWinner().getId().equals(a.getId()) ? a : b);
        } else {
            outcome.setValue(MatchOutcome.NORMAL);
            setsA.setValue(match.getSetsA());
            setsB.setValue(match.getSetsB());
        }
        setsPanel.setVisible(outcome.getValue() == MatchOutcome.NORMAL);
        walkoverPanel.setVisible(outcome.getValue() == MatchOutcome.WALKOVER);
        outcome.addValueChangeListener(e -> {
            setsPanel.setVisible(e.getValue() == MatchOutcome.NORMAL);
            walkoverPanel.setVisible(e.getValue() == MatchOutcome.WALKOVER);
            error.setText("");
        });

        error.getStyle()
                .set("color", "var(--lumo-error-text-color)")
                .set("font-size", "var(--lumo-font-size-s)");

        VerticalLayout content = new VerticalLayout(subtitle, outcome, setsPanel, walkoverPanel, error);
        content.setPadding(false);
        add(content);

        Button save = new Button("Salvează", e -> {
            MatchResultForm result = buildResult();
            if (result == null) {
                return;
            }
            try {
                onSave.accept(result);
                close();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        if (onClear != null && match.isPlayed()) {
            Button clear = new Button("Șterge rezultatul", e -> {
                try {
                    onClear.run();
                    close();
                } catch (RuntimeException ex) {
                    Notifications.error(Notifications.saveError(ex));
                }
            });
            clear.addThemeVariants(ButtonVariant.LUMO_ERROR, ButtonVariant.LUMO_TERTIARY);
            clear.getStyle().set("margin-inline-end", "auto");
            getFooter().add(clear);
        }
        getFooter().add(new Button("Anulează", e -> close()), save);
    }

    private MatchResultForm buildResult() {
        if (outcome.getValue() == MatchOutcome.WALKOVER) {
            if (walkoverWinner.getValue() == null) {
                error.setText("Alegeți jucătorul care câștigă tehnic.");
                return null;
            }
            return MatchResultForm.walkover(walkoverWinner.getValue().getId());
        }
        Integer a = setsA.getValue();
        Integer b = setsB.getValue();
        boolean valid = a != null && b != null
                && ((a == setsToWin && b < setsToWin) || (b == setsToWin && a < setsToWin));
        if (!valid) {
            error.setText("Câștigătorul trebuie să aibă " + setsToWin + " seturi, iar adversarul mai puține.");
            return null;
        }
        return MatchResultForm.sets(a, b);
    }

    /** Scorurile posibile, ca butoane rapide: 3:0, 3:1, 3:2, 2:3, 1:3, 0:3. */
    private static List<String> presets(int setsToWin) {
        List<String> presets = new ArrayList<>();
        for (int lost = 0; lost < setsToWin; lost++) {
            presets.add(setsToWin + ":" + lost);
        }
        for (int lost = setsToWin - 1; lost >= 0; lost--) {
            presets.add(lost + ":" + setsToWin);
        }
        return presets;
    }

    private static Div playerName(TournamentParticipant participant, boolean alignRight) {
        Div name = new Div();
        name.setText(participant.getPlayer().getDisplayName());
        name.getStyle()
                .set("flex", "1")
                .set("font-weight", "500")
                .set("text-align", alignRight ? "right" : "left");
        return name;
    }
}
