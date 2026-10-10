package md.ttm.service.player;

/**
 * Citește orientarea (eticheta EXIF 0x0112) dintr-o imagine JPEG, fără biblioteci suplimentare.
 * Valori: 1 = normal, 3 = întoarsă 180°, 6 = de rotit 90° în sensul acelor de ceas, 8 = 270°.
 */
final class ExifOrientation {

    private ExifOrientation() {
    }

    static int of(byte[] jpeg) {
        try {
            if (jpeg.length < 4 || u8(jpeg, 0) != 0xFF || u8(jpeg, 1) != 0xD8) {
                return 1; // nu e JPEG
            }
            int pos = 2;
            while (pos + 4 <= jpeg.length) {
                if (u8(jpeg, pos) != 0xFF) {
                    return 1;
                }
                int marker = u8(jpeg, pos + 1);
                if (marker == 0xDA || marker == 0xD9) {
                    return 1; // începutul datelor imaginii: fără EXIF
                }
                int length = u16(jpeg, pos + 2, false);
                if (marker == 0xE1 && length >= 8 && pos + 2 + length <= jpeg.length
                        && jpeg[pos + 4] == 'E' && jpeg[pos + 5] == 'x' && jpeg[pos + 6] == 'i' && jpeg[pos + 7] == 'f') {
                    return fromTiff(jpeg, pos + 10, pos + 2 + length);
                }
                pos += 2 + length;
            }
        } catch (RuntimeException e) {
            // date EXIF incomplete: imaginea se folosește așa cum e
        }
        return 1;
    }

    private static int fromTiff(byte[] b, int tiff, int end) {
        boolean little = b[tiff] == 'I' && b[tiff + 1] == 'I';
        int ifd = tiff + (int) u32(b, tiff + 4, little);
        int entries = u16(b, ifd, little);
        for (int i = 0; i < entries; i++) {
            int entry = ifd + 2 + i * 12;
            if (entry + 12 > end) {
                break;
            }
            if (u16(b, entry, little) == 0x0112) {
                int value = u16(b, entry + 8, little);
                return value >= 1 && value <= 8 ? value : 1;
            }
        }
        return 1;
    }

    private static int u8(byte[] b, int i) {
        return b[i] & 0xFF;
    }

    private static int u16(byte[] b, int i, boolean little) {
        return little ? u8(b, i) | (u8(b, i + 1) << 8) : (u8(b, i) << 8) | u8(b, i + 1);
    }

    private static long u32(byte[] b, int i, boolean little) {
        return little
                ? (u8(b, i) | (u8(b, i + 1) << 8) | (u8(b, i + 2) << 16) | ((long) u8(b, i + 3) << 24))
                : (((long) u8(b, i) << 24) | (u8(b, i + 1) << 16) | (u8(b, i + 2) << 8) | u8(b, i + 3));
    }
}
