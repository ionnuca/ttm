package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.checkbox.Checkbox;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.select.Select;
import com.vaadin.flow.component.textfield.BigDecimalField;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import md.ttm.model.tournament.PrizeDistribution;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentFormat;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.ui.components.Notifications;

import java.util.List;
import java.util.function.Consumer;

/**
 * Crearea / editarea unui turneu: nume, dată, format, număr de seturi și,
 * opțional, secțiunea de turneu comercial (câștigători și taxă de participare).
 */
public class TournamentFormDialog extends Dialog {

    private final TextField name = new TextField("Numele turneului");
    private final DatePicker date = new DatePicker("Data");
    private final Select<TournamentFormat> format = new Select<>();
    private final Select<Integer> bestOf = new Select<>();
    private final Checkbox commercial = new Checkbox("Turneu comercial (cu taxă de participare și premii)");
    private final RadioButtonGroup<Integer> winners = new RadioButtonGroup<>("Numărul de câștigători");
    private final BigDecimalField entryFee = new BigDecimalField("Taxa de participare");
    private final Div commercialSection = new Div();

    private final Binder<TournamentForm> binder = new Binder<>(TournamentForm.class);

    /**
     * @param tournament turneul editat sau {@code null} pentru unul nou
     */
    public TournamentFormDialog(Tournament tournament, Consumer<TournamentForm> onSave) {
        boolean creating = tournament == null;
        TournamentForm form = creating ? new TournamentForm() : TournamentForm.from(tournament);
        setHeaderTitle(creating ? "Turneu nou" : "Editare turneu");
        setWidth("min(95vw, 38rem)");

        name.setMaxLength(150);
        date.setI18n(romanianDatePicker());
        format.setLabel("Tipul turneului");
        format.setItems(TournamentFormat.values());
        format.setItemLabelGenerator(TournamentFormat::getLabel);
        format.setValue(TournamentFormat.ROUND_ROBIN);
        format.setHelperText("Alte tipuri vor fi adăugate ulterior");
        format.setReadOnly(TournamentFormat.values().length == 1);
        bestOf.setLabel("Numărul de seturi");
        bestOf.setItems(3, 5, 7);
        bestOf.setItemLabelGenerator(TournamentLabels::bestOf);

        winners.setItems(1, 2, 3);
        winners.setItemLabelGenerator(n -> PrizeDistribution.forWinners(n).getLabel());
        winners.addThemeName("vertical");
        entryFee.setSuffixComponent(new Span("lei"));
        entryFee.setHelperText("Suma acumulată = taxa × numărul de participanți");

        binder.forField(name).asRequired("Introduceți numele turneului")
                .bind(TournamentForm::getName, TournamentForm::setName);
        binder.forField(date).asRequired("Alegeți data")
                .bind(TournamentForm::getDate, TournamentForm::setDate);
        binder.forField(bestOf).asRequired("Alegeți numărul de seturi")
                .bind(TournamentForm::getBestOf, TournamentForm::setBestOf);
        binder.forField(commercial)
                .bind(TournamentForm::isCommercial, TournamentForm::setCommercial);
        binder.forField(winners)
                .withValidator(v -> !commercial.getValue() || v != null, "Alegeți numărul de câștigători")
                .bind(TournamentForm::getWinnersCount, TournamentForm::setWinnersCount);
        binder.forField(entryFee)
                .withValidator(v -> !commercial.getValue() || (v != null && v.signum() > 0),
                        "Introduceți taxa de participare")
                .bind(TournamentForm::getEntryFee, TournamentForm::setEntryFee);
        binder.readBean(form);

        FormLayout commercialForm = new FormLayout(winners, entryFee);
        commercialForm.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        commercialSection.add(commercialForm);
        commercialSection.getStyle()
                .set("padding", "var(--lumo-space-s) var(--lumo-space-m)")
                .set("border-radius", "var(--lumo-border-radius-l)")
                .set("background", "var(--lumo-contrast-5pct)");
        commercialSection.setVisible(commercial.getValue());
        commercial.addValueChangeListener(e -> {
            commercialSection.setVisible(e.getValue());
            if (!e.getValue()) {
                winners.setInvalid(false);
                entryFee.setInvalid(false);
            }
        });

        FormLayout main = new FormLayout(name, date, format, bestOf, commercial);
        main.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1),
                new FormLayout.ResponsiveStep("28rem", 2));
        main.setColspan(name, 2);
        main.setColspan(commercial, 2);
        add(main, commercialSection);

        Button save = new Button(creating ? "Creează turneul" : "Salvează", e -> {
            if (binder.writeBeanIfValid(form)) {
                try {
                    onSave.accept(form);
                    close();
                } catch (RuntimeException ex) {
                    Notifications.error(Notifications.saveError(ex));
                }
            }
        });
        save.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button("Anulează", e -> close()), save);
        name.focus();
    }

    private static DatePicker.DatePickerI18n romanianDatePicker() {
        DatePicker.DatePickerI18n i18n = new DatePicker.DatePickerI18n();
        i18n.setMonthNames(List.of("ianuarie", "februarie", "martie", "aprilie", "mai", "iunie", "iulie",
                "august", "septembrie", "octombrie", "noiembrie", "decembrie"));
        i18n.setWeekdays(List.of("duminică", "luni", "marți", "miercuri", "joi", "vineri", "sâmbătă"));
        i18n.setWeekdaysShort(List.of("du", "lu", "ma", "mi", "jo", "vi", "sâ"));
        i18n.setFirstDayOfWeek(1);
        i18n.setToday("Azi");
        i18n.setCancel("Anulează");
        i18n.setDateFormat("dd.MM.yyyy");
        return i18n;
    }
}
