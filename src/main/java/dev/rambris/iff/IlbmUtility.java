package dev.rambris.iff;

import dev.rambris.iff.codec.IlbmImage;
import dev.rambris.iff.exceptions.IffParseException;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;

/**
 * Utility for converting between {@link IlbmImage} planar pixel data and
 * {@link BufferedImage}.
 *
 * <p>Only indexed-color images are supported. Attempting to convert a
 * non-indexed {@link BufferedImage} throws {@link IffParseException}.
 */
public final class IlbmUtility {

    private IlbmUtility() {}

    /**
     * Converts an {@link IlbmImage} to an indexed-color {@link BufferedImage}.
     *
     * <p>The image must have a {@code CMAP} palette; if absent an
     * {@link IffParseException} is thrown.
     *
     * @throws IffParseException if the image has no palette or no body data
     */
    public static BufferedImage toBufferedImage(IlbmImage image) {
        var bmhd = image.bmhd();
        if (bmhd == null) {
            throw new IffParseException("IlbmImage has no BMHD");
        }
        int[] palette = image.palette();
        if (palette == null || palette.length == 0) {
            throw new IffParseException("Not an indexed ILBM: no CMAP palette");
        }
        byte[] body = image.body();
        if (body == null) {
            throw new IffParseException("IlbmImage has no BODY data");
        }

        int width   = bmhd.width();
        int height  = bmhd.height();
        int planes  = bmhd.planes();

        var cm  = buildColorModel(palette, planes);
        var img = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, cm);
        var raster = (WritableRaster) img.getRaster();

        int rowBytes = ((width + 15) / 16) * 2;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixelValue = 0;
                for (int p = 0; p < planes; p++) {
                    int offset = y * planes * rowBytes + p * rowBytes + (x / 8);
                    int bit    = (body[offset] >> (7 - (x % 8))) & 1;
                    pixelValue |= bit << p;
                }
                raster.setSample(x, y, 0, pixelValue);
            }
        }
        return img;
    }

    /**
     * Converts an indexed-color {@link BufferedImage} to Amiga interleaved planar bytes
     * suitable for use as an ILBM {@code BODY}.
     *
     * @param image  the source image; must use an {@link IndexColorModel}
     * @param planes number of bitplanes to encode (determines bits per pixel)
     * @return interleaved planar data (row-major, plane-major within each row)
     * @throws IffParseException if {@code image} is not indexed-color
     */
    public static byte[] toPlanar(BufferedImage image, int planes) {
        if (!(image.getColorModel() instanceof IndexColorModel)) {
            throw new IffParseException(
                    "Not an indexed-color image: " + image.getColorModel().getClass().getSimpleName());
        }

        int width    = image.getWidth();
        int height   = image.getHeight();
        int rowBytes = ((width + 15) / 16) * 2;
        byte[] body  = new byte[height * planes * rowBytes];
        var raster   = image.getRaster();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = raster.getSample(x, y, 0);
                for (int p = 0; p < planes; p++) {
                    if (((pixel >> p) & 1) != 0) {
                        int offset = y * planes * rowBytes + p * rowBytes + (x / 8);
                        body[offset] |= (byte) (1 << (7 - (x % 8)));
                    }
                }
            }
        }
        return body;
    }

    /**
     * Builds an {@link IndexColorModel} from an {@code 0x00RRGGBB} palette array.
     *
     * @param palette RGB entries (alpha ignored)
     * @param planes  number of bitplanes; determines the model bit depth
     */
    public static IndexColorModel buildColorModel(int[] palette, int planes) {
        int size = palette.length;
        byte[] r = new byte[size];
        byte[] g = new byte[size];
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) {
            r[i] = (byte)((palette[i] >> 16) & 0xFF);
            g[i] = (byte)((palette[i] >> 8)  & 0xFF);
            b[i] = (byte)( palette[i]        & 0xFF);
        }
        int bits = Math.max(1, Math.min(8, planes));
        return new IndexColorModel(bits, size, r, g, b);
    }

    /**
     * Extracts an {@code 0x00RRGGBB} palette array from an indexed-color {@link BufferedImage}.
     *
     * @throws IffParseException if the image is not indexed-color
     */
    public static int[] extractPalette(BufferedImage image) {
        if (!(image.getColorModel() instanceof IndexColorModel icm)) {
            throw new IffParseException(
                    "Not an indexed-color image: " + image.getColorModel().getClass().getSimpleName());
        }
        int size = icm.getMapSize();
        int[] palette = new int[size];
        for (int i = 0; i < size; i++) {
            palette[i] = icm.getRGB(i) & 0x00FFFFFF;
        }
        return palette;
    }
}
