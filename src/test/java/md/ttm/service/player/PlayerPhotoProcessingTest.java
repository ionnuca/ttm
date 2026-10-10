package md.ttm.service.player;

import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class PlayerPhotoProcessingTest {

    @Test
    void pozaMareDeTelefonSeMicsoreazaLa400() throws IOException {
        byte[] photo = jpeg(4032, 3024, false);
        BufferedImage result = decode(PlayerPhotoService.toSquareJpeg(photo));
        assertThat(result.getWidth()).isEqualTo(PlayerPhotoService.SIZE);
        assertThat(result.getHeight()).isEqualTo(PlayerPhotoService.SIZE);
    }

    @Test
    void orientareaExifSeAplica() throws IOException {
        // 200 x 100: jumătatea stângă roșie, cea dreaptă albastră; EXIF 6 = de rotit 90° în sensul acelor de ceas
        byte[] photo = withOrientation(jpeg(200, 100, true), 6);
        assertThat(ExifOrientation.of(photo)).isEqualTo(6);

        BufferedImage result = decode(PlayerPhotoService.toSquareJpeg(photo));
        // după rotire: sus roșu, jos albastru
        assertThat(isRed(result.getRGB(result.getWidth() / 2, 10))).isTrue();
        assertThat(isRed(result.getRGB(result.getWidth() / 2, result.getHeight() - 10))).isFalse();
    }

    @Test
    void faraExifOrientareaENormala() throws IOException {
        assertThat(ExifOrientation.of(jpeg(50, 50, false))).isEqualTo(1);
        assertThat(ExifOrientation.of("nu e o imagine".getBytes())).isEqualTo(1);
    }

    private static boolean isRed(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 150 && c.getBlue() < 100;
    }

    private static BufferedImage decode(byte[] bytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(bytes));
    }

    private static byte[] jpeg(int width, int height, boolean halves) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, halves ? width / 2 : width, height);
        if (halves) {
            g.setColor(Color.BLUE);
            g.fillRect(width / 2, 0, width - width / 2, height);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "jpeg", out);
        return out.toByteArray();
    }

    /** Inserează după SOI un segment APP1 Exif minimal (big-endian) cu eticheta Orientation. */
    private static byte[] withOrientation(byte[] jpeg, int orientation) {
        byte[] tiff = {
                'M', 'M', 0, 42, 0, 0, 0, 8,           // antet TIFF, IFD0 la offset 8
                0, 1,                                   // o intrare
                0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, (byte) orientation, 0, 0, // Orientation, SHORT, 1
                0, 0, 0, 0                              // fără IFD următor
        };
        int length = 2 + 6 + tiff.length;
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.write(jpeg, 0, 2);
        out.write(0xFF);
        out.write(0xE1);
        out.write(length >> 8);
        out.write(length & 0xFF);
        out.writeBytes(new byte[]{'E', 'x', 'i', 'f', 0, 0});
        out.writeBytes(tiff);
        out.write(jpeg, 2, jpeg.length - 2);
        return out.toByteArray();
    }
}
