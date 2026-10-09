package md.ttm.ui.player;

import md.ttm.model.player.PlayHand;
import md.ttm.model.player.Player;
import com.vaadin.flow.component.formlayout.FormLayout;
import com.vaadin.flow.component.html.H4;
import com.vaadin.flow.component.radiobutton.RadioButtonGroup;
import com.vaadin.flow.component.textfield.TextField;
import com.vaadin.flow.data.binder.Binder;

/**
 * Câmpurile pentru mâna de joc și echipament, comune formularului de administrare
 * și paginii „Profilul meu”. Toate sunt opționale.
 */
public final class PlayerDetailsFields {

    private final RadioButtonGroup<PlayHand> playHand = new RadioButtonGroup<>("Mâna de joc");
    private final TextField blade = new TextField("Lemn");
    private final TextField forehandRubber = new TextField("Față forehand");
    private final TextField backhandRubber = new TextField("Față backhand");

    public PlayerDetailsFields(Binder<Player> binder) {
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
    public void addPlayHandTo(FormLayout form) {
        form.add(playHand);
    }

    /** Adaugă secțiunea „Echipament” pe toată lățimea formularului. */
    public void addEquipmentTo(FormLayout form) {
        H4 title = new H4("Echipament");
        title.getStyle().set("margin", "var(--lumo-space-m) 0 0");
        form.add(title, blade, forehandRubber, backhandRubber);
        form.setColspan(title, 2);
        form.setColspan(blade, 2);
    }
}
