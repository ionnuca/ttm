package md.ttm.ui.tournament;

import md.ttm.i18n.Tr;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.IntegerField;
import com.vaadin.flow.data.binder.Binder;
import com.vaadin.flow.data.value.ValueChangeMode;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.model.tournament.PrizeDistribution;
import md.ttm.model.tournament.TournamentFormat;
import md.ttm.service.tournament.GroupStagePlanner;
import md.ttm.service.tournament.TournamentSettings;
import md.ttm.ui.components.Notifications;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * „Începe turneul”: organizatorul (administrator sau manager de turnee) alege câte etape are turneul și tipul primei
 * etape, numărul de seturi și, opțional, turneul comercial, apoi confirmă închiderea înscrierii.
 * <ul>
 *   <li>două etape (implicit): etapa 1 pe grupe; tipul etapei 2 (finale sau tablou) se alege la începerea ei;</li>
 *   <li>o etapă: Round Robin, eliminare directă (olimpic) sau eliminare cu ramura de jos.</li>
 * </ul>
 */
class StartTournamentDialog extends Dialog {

    /** Tipul unui turneu de o etapă. */
    enum SingleStage {
        ROUND_ROBIN(Tr.key("Round robin (fiecare cu fiecare)")),
        SINGLE(Tr.key("Eliminare directă (olimpic)")),
        DOUBLE(Tr.key("Eliminare cu ramura de jos (dublă eliminare)"));

        private final String label;

        SingleStage(String label) {
            this.label = label;
        }

        String getLabel() {
            return Tr.t(label);
        }
    }

    private final RadioButtonGroup<Integer> stages = new RadioButtonGroup<>(Tr.t("Numărul de etape"));
    private final Select<SingleStage> singleType = new Select<>();
    private final Span firstStage = new Span(Tr.t("Etapa 1: grupe, fiecare cu fiecare în grupă"));
    private final IntegerField groupCount = new IntegerField(Tr.t("Numărul de grupe"));
    private final Select<Integer> bestOf = new Select<>();
    private final Checkbox commercial = new Checkbox(Tr.t("Turneu comercial (cu taxă de participare și premii)"));
    private final RadioButtonGroup<Integer> winners = new RadioButtonGroup<>(Tr.t("Numărul de câștigători"));
    private final BigDecimalField entryFee = new BigDecimalField(Tr.t("Taxa de participare"));
    private final Span prizePreview = new Span();
    private final Paragraph summary = new Paragraph();
    private final String tournamentName;
    private final int participants;

    private final Binder<TournamentSettings> binder = new Binder<>(TournamentSettings.class);

    StartTournamentDialog(String tournamentName, int participants, Consumer<TournamentSettings> onStart) {
        this.tournamentName = tournamentName;
        this.participants = participants;
        setHeaderTitle(Tr.t("Începe turneul"));
        setWidth("min(95vw, 38rem)");

        stages.setItems(2, 1);
        stages.setItemLabelGenerator(n -> n == 2 ? Tr.t("Două etape (grupe, apoi finale sau tablou)") : Tr.t("O etapă"));
        stages.setValue(2);
        singleType.setLabel(Tr.t("Tipul turneului"));
        singleType.setItems(SingleStage.values());
        singleType.setItemLabelGenerator(SingleStage::getLabel);
        singleType.setValue(SingleStage.ROUND_ROBIN);
        firstStage.getStyle().set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        groupCount.setMin(1);
        groupCount.setMax(Math.max(1, participants / 2));
        groupCount.setStepButtonsVisible(true);
        groupCount.setValueChangeMode(ValueChangeMode.EAGER);
        bestOf.setLabel(Tr.t("Numărul de seturi"));
        bestOf.setItems(3, 5, 7);
        bestOf.setItemLabelGenerator(n -> "Best of " + n);
        bestOf.addValueChangeListener(e -> bestOf.setHelperText(
                e.getValue() == null ? null : Tr.t("Câștigă primul la {0} seturi", e.getValue() / 2 + 1)));

        winners.setItems(1, 2, 3);
        winners.setItemLabelGenerator(n -> PrizeDistribution.forWinners(n).getLabel());
        winners.addThemeName("vertical");
        entryFee.setSuffixComponent(new Span("lei"));
        entryFee.setValueChangeMode(ValueChangeMode.EAGER);

        binder.forField(groupCount)
                .withValidator(v -> !isGroups() || validGroups(),
                        Tr.t("Cel puțin o grupă, cu minimum 2 jucători în fiecare"))
                .bind(TournamentSettings::getGroupCount, TournamentSettings::setGroupCount);
        binder.forField(bestOf).asRequired(Tr.t("Alegeți numărul de seturi"))
                .bind(TournamentSettings::getBestOf, TournamentSettings::setBestOf);
        binder.forField(commercial)
                .bind(TournamentSettings::isCommercial, TournamentSettings::setCommercial);
        binder.forField(winners)
                .withValidator(v -> !commercial.getValue() || v != null, Tr.t("Alegeți numărul de câștigători"))
                .bind(TournamentSettings::getWinnersCount, TournamentSettings::setWinnersCount);
        binder.forField(entryFee)
                .withValidator(v -> !commercial.getValue() || (v != null && v.signum() > 0),
                        Tr.t("Introduceți taxa de participare"))
                .bind(TournamentSettings::getEntryFee, TournamentSettings::setEntryFee);
        TournamentSettings settings = new TournamentSettings();
        settings.setGroupCount(Math.max(1, Math.min(Math.max(2, participants / 4), participants / 2)));
        binder.readBean(settings);

        // --- secțiunea comercială ---
        prizePreview.getStyle().set("font-size", "var(--lumo-font-size-s)");
        FormLayout commercialForm = new FormLayout(winners, entryFee);
        commercialForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        Div commercialSection = new Div(commercialForm, prizePreview);
        commercialSection.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)");
        commercialSection.setVisible(false);
        commercial.addValueChangeListener(e -> {
            commercialSection.setVisible(e.getValue());
            if (!e.getValue()) {
                winners.setInvalid(false);
                entryFee.setInvalid(false);
            }
            update();
        });
        winners.addValueChangeListener(e -> update());
        entryFee.addValueChangeListener(e -> update());
        stages.addValueChangeListener(e -> update());
        singleType.addValueChangeListener(e -> update());
        groupCount.addValueChangeListener(e -> update());

        summary.getStyle()
                .set("margin", "var(--lumo-space-m) 0 0")
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");

        FormLayout main = new FormLayout(stages, singleType, firstStage, groupCount, bestOf, commercial);
        main.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        main.setColspan(stages, 2);
        main.setColspan(firstStage, 2);
        main.setColspan(commercial, 2);
        add(main, commercialSection, summary);
        update();

        Button start = new Button(Tr.t("Începe turneul"), VaadinIcon.PLAY.create(), e -> {
            if (binder.writeBeanIfValid(settings)) {
                settings.setFormat(format());
                settings.setKnockoutType(knockoutType());
                try {
                    onStart.accept(settings);
                    close();
                } catch (RuntimeException ex) {
                    Notifications.error(Notifications.saveError(ex));
                }
            }
        });
        start.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SUCCESS);
        getFooter().add(new Button(Tr.t("Anulează"), e -> close()), start);
    }

    private boolean isGroups() {
        return Integer.valueOf(2).equals(stages.getValue());
    }

    private boolean isSingleKnockout() {
        return !isGroups() && singleType.getValue() != null && singleType.getValue() != SingleStage.ROUND_ROBIN;
    }

    private TournamentFormat format() {
        if (isGroups()) {
            return TournamentFormat.GROUPS;
        }
        return isSingleKnockout() ? TournamentFormat.KNOCKOUT : TournamentFormat.ROUND_ROBIN;
    }

    private KnockoutType knockoutType() {
        if (!isSingleKnockout()) {
            return null;
        }
        return singleType.getValue() == SingleStage.DOUBLE ? KnockoutType.DOUBLE : KnockoutType.SINGLE;
    }

    private boolean validGroups() {
        Integer g = groupCount.getValue();
        return g != null && g >= 1 && participants >= g * 2;
    }

    /** Actualizează pe loc câmpurile vizibile și descrierea grupelor, a meciurilor și a premiilor. */
    private void update() {
        boolean groups = isGroups();
        singleType.setVisible(!groups);
        firstStage.setVisible(groups);
        groupCount.setVisible(groups);
        if (groups) {
            if (validGroups()) {
                List<Integer> sizes = GroupStagePlanner.groupSizes(participants, groupCount.getValue());
                int matches = sizes.stream().mapToInt(n -> n * (n - 1) / 2).sum();
                groupCount.setHelperText(Tr.t("Jucători pe grupă: {0} (pe urne după rating, tragere la sorți)",
                        sizes.stream().map(String::valueOf).collect(Collectors.joining(", "))));
                summary.setText(Tr.t("„{0}”: înscrierea se închide. Se formează {1} grupe din {2} jucători și se generează {3} meciuri pentru etapa 1. Tipul etapei 2 (finale Round Robin, tablou olimpic sau cu ramura de jos) îl alegeți după grupe, la începerea ei.",
                        tournamentName, sizes.size(), participants, matches));
            } else {
                groupCount.setHelperText(Tr.t("Cel puțin 2 jucători în fiecare grupă"));
                summary.setText("");
            }
        } else if (isSingleKnockout()) {
            int size = 1;
            while (size < participants) {
                size *= 2;
            }
            String text = Tr.t("„{0}”: înscrierea se închide. Toți cei {1} jucători intră în tabloul eliminatoriu, așezați după rating.",
                    tournamentName, participants);
            if (size > participants) {
                text += " " + Tr.t("Tabloul are {0} locuri: cei mai bine clasați {1} trec direct în turul 2.", size, size - participants);
            }
            text += " " + (singleType.getValue() == SingleStage.SINGLE
                    ? Tr.t("Cine pierde iese din turneu. Finala decide locurile 1 și 2, iar cei învinși în semifinale joacă meciul pentru locul 3.")
                    : Tr.t("Finala tabloului principal decide locurile 1 și 2. Cine pierde înainte de finală trece în ramura de jos, unde cine pierde iese; finala ramurii de jos decide locurile 3 și 4."));
            summary.setText(text);
        } else {
            int matches = participants * (participants - 1) / 2;
            int rounds = participants % 2 == 0 ? participants - 1 : participants;
            summary.setText(Tr.t("„{0}”: înscrierea se închide. Se formează grupa cu {1} jucători, ordonată după rating, și se generează {2} meciuri în {3} tururi.",
                    tournamentName, participants, matches, rounds));
        }
        updatePrizePreview();
    }

    /** Suma acumulată și premiile pentru configurarea aleasă. */
    private void updatePrizePreview() {
        BigDecimal fee = entryFee.getValue();
        boolean groups = isGroups();
        winners.setLabel(groups ? Tr.t("Premiați din Finala 1 (Tabloul 1)") : Tr.t("Numărul de câștigători"));
        if (!commercial.getValue() || fee == null || fee.signum() <= 0 || winners.getValue() == null) {
            prizePreview.setText(Tr.t("Suma acumulată = taxa × {0} participanți.", participants)
                    + (groups ? " " + Tr.t("În etapa 2, câștigătorul Finalei 2 (sau al Tabloului 2) primește cât taxa de participare; restul se împarte între premiații Finalei 1 (Tabloului 1).") : ""));
            return;
        }
        BigDecimal pool = fee.multiply(BigDecimal.valueOf(participants));
        BigDecimal toSplit = groups ? pool.subtract(fee) : pool;
        List<BigDecimal> amounts = PrizeDistribution.forWinners(winners.getValue()).split(toSplit);
        List<String> places = new ArrayList<>();
        for (int i = 0; i < amounts.size(); i++) {
            places.add(Tr.t("locul {0}", i + 1) + ": " + TournamentLabels.money(amounts.get(i)));
        }
        if (groups) {
            places.add(Tr.t("câștigătorul Finalei 2 (Tabloului 2)") + ": " + TournamentLabels.money(fee));
        }
        prizePreview.setText(Tr.t("Suma acumulată: {0} ({1} participanți) — {2}.",
                TournamentLabels.money(pool), participants, String.join(", ", places)));
    }
}
