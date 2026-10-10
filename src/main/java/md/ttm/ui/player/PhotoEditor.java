package md.ttm.ui.player;

import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.orderedlayout.FlexComponent;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.component.upload.Upload;
import com.vaadin.flow.component.upload.receivers.MemoryBuffer;
import md.ttm.common.BusinessException;
import md.ttm.model.player.Player;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import md.ttm.service.player.PlayerPhotoService;
import md.ttm.ui.components.Notifications;
import md.ttm.ui.components.PlayerAvatar;

import java.io.IOException;
import java.io.InputStream;

/** Poza de profil: avatarul mare, „Alege o poză” și „Șterge poza”. */
public class PhotoEditor extends HorizontalLayout {

    private static final Logger log = LoggerFactory.getLogger(PhotoEditor.class);

    private final Player player;
    private final PlayerPhotoService photoService;
    private final Runnable onChange;
    private Avatar avatar;
    private final Button delete = new Button("Șterge poza", VaadinIcon.TRASH.create());

    public PhotoEditor(Player player, PlayerPhotoService photoService, Runnable onChange) {
        this(player, photoService, onChange, true);
    }

    /**
     * @param showAvatar {@code false} = doar butoanele (avatarul e afișat în altă parte a paginii,
     *                   iar {@code onChange} o reîmprospătează)
     */
    public PhotoEditor(Player player, PlayerPhotoService photoService, Runnable onChange, boolean showAvatar) {
        this.player = player;
        this.photoService = photoService;
        this.onChange = onChange;
        setAlignItems(FlexComponent.Alignment.CENTER);
        getStyle().set("flex-wrap", "wrap");

        MemoryBuffer buffer = new MemoryBuffer();
        Upload upload = new Upload(buffer);
        upload.setAcceptedFileTypes("image/jpeg", "image/png", ".jpg", ".jpeg", ".png");
        upload.setMaxFiles(1);
        upload.setMaxFileSize(PlayerPhotoService.MAX_UPLOAD_BYTES);
        upload.setDropAllowed(false);
        Button choose = new Button("Alege o poză", VaadinIcon.CAMERA.create());
        upload.setUploadButton(choose);
        upload.addSucceededListener(e -> {
            try (InputStream in = buffer.getInputStream()) {
                photoService.save(player.getId(), in.readAllBytes());
                Notifications.success("Poza a fost salvată");
                changed();
            } catch (BusinessException ex) {
                Notifications.error(ex.getMessage());
            } catch (IOException | RuntimeException ex) {
                log.warn("Poza jucătorului {} nu a putut fi salvată", player.getId(), ex);
                Notifications.error("Poza nu a putut fi salvată. Încercați o altă poză (JPEG sau PNG).");
            }
            upload.clearFileList();
        });
        upload.addFileRejectedListener(e -> Notifications.error(
                "Alegeți o poză JPEG sau PNG de cel mult 20 MB"));
        upload.addFailedListener(e -> {
            log.warn("Încărcarea pozei a eșuat ({}, {} octeți)", e.getMIMEType(), e.getContentLength(), e.getReason());
            Notifications.error("Încărcarea pozei a eșuat. Verificați conexiunea și încercați din nou.");
            upload.clearFileList();
        });

        delete.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_ERROR);
        delete.addClickListener(e -> {
            try {
                photoService.delete(player.getId());
                Notifications.success("Poza a fost ștearsă");
                changed();
            } catch (RuntimeException ex) {
                Notifications.error(Notifications.saveError(ex));
            }
        });

        Span hint = new Span("JPEG sau PNG; se decupează pătrat și se micșorează automat.");
        hint.getStyle().set("font-size", "var(--lumo-font-size-xs)").set("color", "var(--lumo-secondary-text-color)");
        VerticalLayout controls = new VerticalLayout(new HorizontalLayout(upload, delete), hint);
        controls.setPadding(false);
        controls.setSpacing(false);
        controls.setWidth(null);

        avatar = createAvatar();
        avatar.setVisible(showAvatar);
        add(avatar, controls);
    }

    private Avatar createAvatar() {
        Long version = photoService.version(player.getId());
        delete.setVisible(version != null);
        return PlayerAvatar.of(player, version, "5.5rem");
    }

    private void changed() {
        Avatar updated = createAvatar();
        updated.setVisible(avatar.isVisible());
        replace(avatar, updated);
        avatar = updated;
        if (onChange != null) {
            onChange.run();
        }
    }
}
