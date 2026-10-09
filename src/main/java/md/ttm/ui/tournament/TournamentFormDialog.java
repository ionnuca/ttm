package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.datepicker.DatePicker;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import md.ttm.model.tournament.Tournament;
import md.ttm.service.tournament.TournamentForm;
import md.ttm.ui.components.Notifications;

import java.util.List;
import java.util.function.Consumer;

/**
 * Crearea / editarea unui turneu: doar numele și data. Tipul, numărul de seturi și
 * opțiunea de turneu comercial se aleg la începerea turneului ({@link StartTournamentDialog}).
 */
public class TournamentFormDialog extends Dialog {

    private final TextField name = new TextField("Numele turneului");
    private final DatePicker date = new DatePicker("Data");
    private final Binder<TournamentForm> binder = new Binder<>(TournamentForm.class);

    /**
     * @param tournament turneul editat sau {@code null} pentru unul nou
     */
    public TournamentFormDialog(Tournament tournament, Consumer<TournamentForm> onSave) {
        boolean creating = tournament == null;
        TournamentForm form = creating ? new TournamentForm() : TournamentForm.from(tournament);
        setHeaderTitle(creating ? "Turneu nou" : "Editare turneu");
        setWidth("min(95vw, 32rem)");

        name.setMaxLength(150);
        date.setI18n(romanianDatePicker());

        binder.forField(name).asRequired("Introduceți numele turneului")
                .bind(TournamentForm::getName, TournamentForm::setName);
        binder.forField(date).asRequired("Alegeți data")
                .bind(TournamentForm::getDate, TournamentForm::setDate);
        binder.readBean(form);

        FormLayout layout = new FormLayout(name, date);
        layout.setResponsiveSteps(new FormLayout.ResponsiveStep("0", 1));
        Paragraph note = new Paragraph("După crearea turneului se deschide înscrierea. Tipul turneului, "
                + "numărul de seturi și opțiunea de turneu comercial se aleg la „Începe turneul”.");
        note.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)")
                .set("margin-bottom", "0");
        add(layout, note);

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
