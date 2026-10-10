package md.ttm.ui.components;

import com.vaadin.flow.component.avatar.Avatar;
import md.ttm.model.player.Player;
import md.ttm.web.PlayerPhotoController;

/** Avatarul unui jucător: poza, dacă are, altfel inițialele pe o culoare stabilă. */
public final class PlayerAvatar {

    private PlayerAvatar() {
    }

    /**
     * @param photoVersion versiunea pozei (din {@code PlayerPhotoService}); {@code null} dacă nu are poză
     * @param size         mărimea CSS, de ex. {@code "2rem"}; {@code null} = mărimea implicită
     */
    public static Avatar of(Player player, Long photoVersion, String size) {
        Avatar avatar = new Avatar(player.getDisplayName());
        if (player.getId() != null) {
            avatar.setColorIndex((int) (player.getId() % 7));
        }
        String url = player.getId() == null ? null : PlayerPhotoController.url(player.getId(), photoVersion);
        if (url != null) {
            avatar.setImage(url);
        }
        if (size != null) {
            avatar.getStyle().set("--vaadin-avatar-size", size).set("flex-shrink", "0");
        }
        return avatar;
    }
}
