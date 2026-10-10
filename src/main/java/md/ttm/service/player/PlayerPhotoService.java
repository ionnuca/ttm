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
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Iterator;
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
    /** Latura mare a variantei pentru vizualizare. */
    public static final int FULL_SIZE = 1600;
    public static final int MAX_UPLOAD_BYTES = 20 * 1024 * 1024;
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
            throw new BusinessException("Imaginea e prea mare (maximum 20 MB)");
        }
        BufferedImage source = load(upload);
        PlayerPhoto photo = photoRepository.findById(playerId).orElseGet(() -> new PlayerPhoto(playerId));
        photo.replace(toJpeg(squareThumbnail(source)), toJpeg(fitWithin(source, FULL_SIZE)), JPEG);
        photoRepository.save(photo);
    }

    /**
     * Citește imaginea. Pozele mari de pe telefon (12–48 MP) se citesc direct micșorate (subeșantionare),
     * ca să nu ocupe sute de MB de memorie: latura mică rămâne cel puțin de două ori {@value #SIZE} px.
     */
    private static BufferedImage read(byte[] upload) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(upload))) {
            Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
            if (readers == null || !readers.hasNext()) {
                throw unreadable();
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                // destul pentru miniatură (2 x 400 pe latura mică) și pentru varianta mare (1600 pe latura mare)
                int step = Math.max(1, Math.min(Math.min(width, height) / (2 * SIZE), Math.max(width, height) / FULL_SIZE));
                ImageReadParam param = reader.getDefaultReadParam();
                if (step > 1) {
                    param.setSourceSubsampling(step, step, 0, 0);
                }
                return reader.read(0, param);
            } finally {
                reader.dispose();
            }
        } catch (IOException | RuntimeException e) {
            if (e instanceof BusinessException business) {
                throw business;
            }
            throw unreadable();
        }
    }

    private static BusinessException unreadable() {
        return new BusinessException("Imaginea nu poate fi citită; folosiți o poză JPEG sau PNG");
    }

    /** Pozele de telefon sunt adesea salvate „culcate”, cu orientarea corectă doar în datele EXIF. */
    private static BufferedImage rotate(BufferedImage image, int orientation) {
        int quarterTurns = switch (orientation) {
            case 6 -> 1;  // 90° în sensul acelor de ceas
            case 3 -> 2;  // 180°
            case 8 -> 3;  // 270°
            default -> 0;
        };
        if (quarterTurns == 0) {
            return image;
        }
        int w = image.getWidth();
        int h = image.getHeight();
        boolean swap = quarterTurns % 2 == 1;
        BufferedImage rotated = new BufferedImage(swap ? h : w, swap ? w : h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = rotated.createGraphics();
        try {
            AffineTransform t = new AffineTransform();
            switch (quarterTurns) {
                case 1 -> { t.translate(h, 0); t.quadrantRotate(1); }
                case 2 -> { t.translate(w, h); t.quadrantRotate(2); }
                default -> { t.translate(0, w); t.quadrantRotate(3); }
            }
            g.drawImage(image, t, null);
        } finally {
            g.dispose();
        }
        return rotated;
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

    /** Imaginea încărcată, citită (micșorată dacă e foarte mare) și rotită după EXIF. */
    static BufferedImage load(byte[] upload) {
        return rotate(read(upload), ExifOrientation.of(upload));
    }

    static byte[] toSquareJpeg(byte[] upload) {
        return toJpeg(squareThumbnail(load(upload)));
    }

    /** Miniatura: pătratul din mijloc, cel mult {@value #SIZE} x {@value #SIZE}. */
    static BufferedImage squareThumbnail(BufferedImage source) {
        int side = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - side) / 2;
        int y = (source.getHeight() - side) / 2;
        int target = Math.min(SIZE, side);
        return draw(source, x, y, side, side, target, target);
    }

    /** Toată poza, micșorată proporțional ca latura mare să fie cel mult {@code max} (fără mărire). */
    static BufferedImage fitWithin(BufferedImage source, int max) {
        double scale = Math.min(1.0, (double) max / Math.max(source.getWidth(), source.getHeight()));
        int w = Math.max(1, (int) Math.round(source.getWidth() * scale));
        int h = Math.max(1, (int) Math.round(source.getHeight() * scale));
        return draw(source, 0, 0, source.getWidth(), source.getHeight(), w, h);
    }

    /** Copiază zona (x, y, w, h) din imagine la mărimea (tw, th), pe fond alb (pentru PNG transparent). */
    private static BufferedImage draw(BufferedImage source, int x, int y, int w, int h, int tw, int th) {
        BufferedImage result = new BufferedImage(tw, th, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = result.createGraphics();
        try {
            g.setColor(Color.WHITE);
            g.fillRect(0, 0, tw, th);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.drawImage(source, 0, 0, tw, th, x, y, x + w, y + h, null);
        } finally {
            g.dispose();
        }
        return result;
    }

    static byte[] toJpeg(BufferedImage image) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.85f);
            writer.write(null, new IIOImage(image, null, null), param);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } finally {
            writer.dispose();
        }
        return out.toByteArray();
    }
}
