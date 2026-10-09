package md.ttm.ui;

import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;
import md.ttm.player.PlayHand;
import md.ttm.player.Player;

/**
 * Câmpurile pentru mâna de joc și echipament, comune formularului de administrare
 * și paginii „Profilul meu”. Toate sunt opționale.
 */
final class PlayerDetailsFields {

    final RadioButtonGroup<PlayHand> playHand = new RadioButtonGroup<>("Mâna de joc");
    final TextField blade = new TextField("Lemn");
    final TextField forehandRubber = new TextField("Față forehand");
    final TextField backhandRubber = new TextField("Față backhand");

    PlayerDetailsFields(Binder<Player> binder) {
        playHand.setItems(PlayHand.values());
        playHand.setItemLabelGenerator(PlayHand::getLabel);
        blade.setPlaceholder("ex. Butterfly Viscaria");
        forehandRubber.setPlaceholder("ex. Tenergy 05");
        backhandRubber.setPlaceholder("ex. Dignics 09C");
        for (TextField field : new TextField[]{blade, forehandRubber, backhandRubber}) {
            field.setMaxLength(100);
            field.setClearButtonVisible(true);
        }

        binder.forField(playHand).bind("playHand");
        binder.forField(blade).bind("blade");
        binder.forField(forehandRubber).bind("forehandRubber");
        binder.forField(backhandRubber).bind("backhandRubber");
    }

    /** Adaugă câmpul pentru mână (lângă stilul de joc). */
    void addPlayHandTo(FormLayout form) {
        form.add(playHand);
    }

    /** Adaugă secțiunea „Echipament” pe toată lățimea formularului. */
    void addEquipmentTo(FormLayout form) {
        H4 title = new H4("Echipament");
        title.getStyle().set("margin", "var(--lumo-space-m) 0 0");
        form.add(title, blade, forehandRubber, backhandRubber);
        form.setColspan(title, 2);
        form.setColspan(blade, 2);
    }
}
