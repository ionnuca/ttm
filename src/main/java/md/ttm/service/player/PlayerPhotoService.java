package md.ttm.service.player;

import md.ttm.common.BusinessException;
import md.ttm.model.player.PlayerPhoto;
import md.ttm.model.user.AppUser;
import md.ttm.repository.AppUserRepository;
import md.ttm.repository.PlayerPhotoRepository;
import md.ttm.repository.PlayerRepository;
import md.ttm.security.SecurityUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Pozele de profil. Oricine le poate vedea; jucătorul își schimbă propria poză,
 * iar administratorul poza oricărui jucător. Imaginea se decupează pătrat (centrat)
 * și se micșorează la {@value #SIZE} x {@value #SIZE} pixeli, JPEG.
 */
@Service
@Transactional(readOnly = true)
public class PlayerPhotoService {

    public static final int SIZE = 400;
    public static final int MAX_UPLOAD_BYTES = 10 * 1024 * 1024;
    private static final String JPEG = "image/jpeg";

    private final PlayerPhotoRepository photoRepository;
    private final PlayerRepository playerRepository;
    private final AppUserRepository userRepository;

    public PlayerPhotoService(PlayerPhotoRepository photoRepository, PlayerRepository playerRepository,
                              AppUserRepository userRepository) {
        this.photoRepository = photoRepository;
        this.playerRepository = playerRepository;
        this.userRepository = userRepository;
    }

    public Optional<PlayerPhoto> find(Long playerId) {
        return photoRepository.findById(playerId);
    }

    /** Pentru fiecare jucător cu poză: un număr care se schimbă la fiecare poză nouă (pentru cache-ul browserului). */
    public Map<Long, Long> versions() {
        Map<Long, Long> versions = new HashMap<>();
        photoRepository.findAllStamps().forEach(s -> versions.put(s.getPlayerId(), s.getUpdatedAt().toEpochMilli()));
        return versions;
    }

    public Long version(Long playerId) {
        return photoRepository.findById(playerId).map(p -> p.getUpdatedAt().toEpochMilli()).orElse(null);
    }

    /** Dacă utilizatorul curent poate schimba poza jucătorului (a lui însuși sau, ca administrator, a oricui). */
    public boolean canEdit(Long playerId) {
        if (SecurityUtils.isAdmin()) {
            return true;
        }
        return SecurityUtils.currentUsername()
                .flatMap(userRepository::findByUsername)
                .map(AppUser::getPlayer)
                .map(p -> Objects.equals(p.getId(), playerId))
                .orElse(false);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void save(Long playerId, byte[] upload) {
        requireCanEdit(playerId);
        if (!playerRepository.existsById(playerId)) {
            throw new BusinessException("Jucătorul nu mai există");
        }
        if (upload == null || upload.length == 0) {
            throw new BusinessException("Fișierul este gol");
        }
        if (upload.length > MAX_UPLOAD_BYTES) {
            throw new BusinessException("Imaginea e prea mare (maximum 10 MB)");
        }
        byte[] jpeg = toSquareJpeg(upload);
        PlayerPhoto photo = photoRepository.findById(playerId).orElseGet(() -> new PlayerPhoto(playerId));
        photo.replace(jpeg, JPEG);
        photoRepository.save(photo);
    }

    @PreAuthorize("isAuthenticated()")
    @Transactional
    public void delete(Long playerId) {
        requireCanEdit(playerId);
        photoRepository.findById(playerId).ifPresent(photoRepository::delete);
    }

    private void requireCanEdit(Long playerId) {
        if (!canEdit(playerId)) {
            throw new BusinessException("Puteți schimba doar propria poză");
        }
    }

    static byte[] toSquareJpeg(byte[] upload) {
        BufferedImage source;
        try {
            source = ImageIO.read(new ByteArrayInputStream(upload));
        } catch (IOException e) {
            source = null;
        }
        if (source == null) {
            throw new BusinessException("Imaginea nu poate fi citită; folosiți o poză JPEG sau PNG");
        }
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        int target = Math.min(SIZE, side);

        BufferedImage result = new BufferedImage(target, target, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, target, target);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, target, target, x, y, x + side, y + side, null);
        } finally {
            g.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(result, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
