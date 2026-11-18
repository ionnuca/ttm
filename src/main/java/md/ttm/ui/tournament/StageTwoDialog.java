package md.ttm.ui.tournament;

import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Paragraph;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import md.ttm.i18n.Tr;
import md.ttm.model.tournament.KnockoutType;
import md.ttm.model.tournament.Tournament;
import md.ttm.model.tournament.TournamentFormat;

import java.util.function.Consumer;

/**
 * Primul pas din „Începe etapa 2”: tipul etapei 2, ales abia acum (nu la începerea turneului) –
 * finale Round Robin, tablou olimpic sau tablou cu ramura de jos. Pasul următor configurează tipul ales.
 */
class StageTwoDialog extends Dialog {

    enum Type {
        FINALS(Tr.key("Finale (Round Robin): Finala 1, Finala 2 …")),
        SINGLE(Tr.key("Tablou eliminatoriu olimpic")),
        DOUBLE(Tr.key("Tablou eliminatoriu cu ramura de jos (dublă eliminare)"));

        private final String label;

        Type(String label) {
            this.label = label;
        }

        String getLabel() {
            return Tr.t(label);
        }
    }

    /** @param onNext tipul de tablou ales sau {@code null} pentru finale */
    StageTwoDialog(Tournament tournament, Consumer<KnockoutType> onNext) {
        setHeaderTitle(Tr.t("Începe etapa 2"));
        setWidth("min(95vw, 32rem)");

        RadioButtonGroup<Type> type = new RadioButtonGroup<>(Tr.t("Tipul etapei 2"));
        type.setItems(Type.values());
        type.setItemLabelGenerator(Type::getLabel);
        type.addThemeName("vertical");
        type.setValue(initial(tournament));

        Paragraph note = new Paragraph(Tr.t("La pasul următor alegeți câți jucători din fiecare grupă trec în primul tabel; ceilalți joacă pentru câștigătorul părții a doua."));
        note.getStyle()
                .set("font-size", "var(--lumo-font-size-s)")
                .set("color", "var(--lumo-secondary-text-color)");
        add(type, note);

        Button next = new Button(Tr.t("Continuă"), VaadinIcon.ARROW_RIGHT.create(), e -> {
            close();
            onNext.accept(switch (type.getValue()) {
                case FINALS -> null;
                case SINGLE -> KnockoutType.SINGLE;
                case DOUBLE -> KnockoutType.DOUBLE;
            });
        });
        next.setIconAfterText(true);
        next.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
        getFooter().add(new Button(Tr.t("Anulează"), e -> close()), next);
    }

    /** Implicit: alegerea de dinainte, dacă etapa 2 a mai fost pornită (de exemplu, înainte unei corecturi din grupe). */
    private static Type initial(Tournament tournament) {
        if (tournament.getFormat() == TournamentFormat.GROUPS_KNOCKOUT) {
            return tournament.getKnockoutType() == KnockoutType.DOUBLE ? Type.DOUBLE : Type.SINGLE;
        }
        return Type.FINALS;
    }
}
