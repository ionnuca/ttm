package md.ttm.ui.components;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.html.Image;
import com.vaadin.flow.component.icon.VaadinIcon;
import md.ttm.model.player.Player;
import md.ttm.web.PlayerPhotoController;

/** Poza unui jucător, mărită, într-o fereastră care se închide la apăsare, cu ✕ sau cu Esc. */
public final class PhotoViewer {

    private PhotoViewer() {
    }

    public static void open(Player player, Long photoVersion) {
        String url = PlayerPhotoController.fullUrl(player.getId(), photoVersion);
        if (url == null) {
            return;
        }
        Dialog dialog = new Dialog();
        dialog.setHeaderTitle(player.getDisplayName());
        Button close = new Button(VaadinIcon.CLOSE.create(), e -> dialog.close());
        close.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ICON);
        close.setAriaLabel("Închide");
        dialog.getHeader().add(close);

        Image image = new Image(url, "Poza lui " + player.getDisplayName());
        image.getStyle()
                .set("display", "block")
                .set("max-width", "min(90vw, 1000px)")
                .set("max-height", "75vh")
                .set("width", "auto")
                .set("height", "auto")
                .set("margin", "0 auto")
                .set("border-radius", "var(--lumo-border-radius-m)")
                .set("cursor", "zoom-out");
        image.addClickListener(e -> dialog.close());
        dialog.add(image);
        dialog.setCloseOnOutsideClick(true);
        dialog.setCloseOnEsc(true);
        dialog.open();
    }

    /** Face avatarul apăsabil (doar dacă jucătorul are poză): deschide poza mărită. */
    public static Avatar clickable(Avatar avatar, Player player, Long photoVersion) {
        if (photoVersion != null) {
            avatar.getStyle().set("cursor", "zoom-in");
            avatar.getElement().setAttribute("title", "Vezi poza mărită");
            avatar.getElement().setAttribute("role", "button");
            avatar.getElement().addEventListener("click", e -> open(player, photoVersion));
        }
        return avatar;
    }
}
