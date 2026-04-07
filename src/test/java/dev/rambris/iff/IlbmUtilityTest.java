package dev.rambris.iff;

import dev.rambris.iff.codec.BmhdChunk;
import dev.rambris.iff.codec.IlbmImage;
import dev.rambris.iff.exceptions.IffParseException;
import org.junit.jupiter.api.Test;

import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;

import static org.junit.jupiter.api.Assertions.*;

class IlbmUtilityTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Builds a 4×2, 2-plane IlbmImage where pixel (x, y) = (y * 4 + x) % 4.
     *
     * <p>rowBytes = ((4+15)/16)*2 = 2.
     * The planar body encodes each row as two bit-plane rows, each 2 bytes wide.
     */
    private static IlbmImage buildTestImage() {
        int width    = 4;
        int height   = 2;
        int planes   = 2;
        int rowBytes = 2;

        // 4 colours: 0=black, 1=red, 2=green, 3=blue
        int[] palette = {0x000000, 0xFF0000, 0x00FF00, 0x0000FF};
        var bmhd = new BmhdChunk(width, height, 0, 0, planes,
                BmhdChunk.COMPRESSION_NONE, BmhdChunk.COMPRESSION_NONE, 0, 1, 1, width, height);

        // Pixel layout (row-major): row0 = [0,1,2,3], row1 = [0,1,2,3]
        // Plane 0 (LSB): row0 bits for x=0..3: 0=0,1=1,2=0,3=1 → 0b01010000 = 0x50, pad=0x00
        //                row1 same: 0x50, 0x00
        // Plane 1 (MSB): row0 bits for x=0..3: 0=0,1=0,2=1,3=1 → 0b00110000 = 0x30, pad=0x00
        //                row1 same: 0x30, 0x00
        //
        // Interleaved body layout (height * planes * rowBytes = 2*2*2 = 8 bytes):
        //   row0: plane0 [0x50, 0x00], plane1 [0x30, 0x00]
        //   row1: plane0 [0x50, 0x00], plane1 [0x30, 0x00]
        byte[] body = {
                0x50, 0x00, 0x30, 0x00,   // row 0: plane0, plane1
                0x50, 0x00, 0x30, 0x00    // row 1: plane0, plane1
        };

        return new IlbmImage(bmhd, palette, 0, body);
    }

    // -------------------------------------------------------------------------
    // toBufferedImage
    // -------------------------------------------------------------------------

    @Test
    void toBufferedImage_pixelsMatchExpected() {
        var img    = IlbmUtility.toBufferedImage(buildTestImage());
        var raster = img.getRaster();

        // Row 0: pixels 0,1,2,3
        assertEquals(0, raster.getSample(0, 0, 0));
        assertEquals(1, raster.getSample(1, 0, 0));
        assertEquals(2, raster.getSample(2, 0, 0));
        assertEquals(3, raster.getSample(3, 0, 0));

        // Row 1 same pattern
        assertEquals(0, raster.getSample(0, 1, 0));
        assertEquals(1, raster.getSample(1, 1, 0));
        assertEquals(2, raster.getSample(2, 1, 0));
        assertEquals(3, raster.getSample(3, 1, 0));
    }

    @Test
    void toBufferedImage_colorModelMatchesPalette() {
        var ilbm = buildTestImage();
        var img  = IlbmUtility.toBufferedImage(ilbm);

        assertTrue(img.getColorModel() instanceof IndexColorModel);
        var icm = (IndexColorModel) img.getColorModel();

        // Colour 1 should be red (R=255, G=0, B=0)
        assertEquals(255, icm.getRed(1));
        assertEquals(0,   icm.getGreen(1));
        assertEquals(0,   icm.getBlue(1));
    }

    @Test
    void toBufferedImage_noPalette_throws() {
        var bmhd  = buildTestImage().bmhd();
        var image = new IlbmImage(bmhd, null, 0, buildTestImage().body());
        assertThrows(IffParseException.class, () -> IlbmUtility.toBufferedImage(image));
    }

    @Test
    void toBufferedImage_noBody_throws() {
        var ilbm = buildTestImage();
        var image = new IlbmImage(ilbm.bmhd(), ilbm.palette(), 0, null);
        assertThrows(IffParseException.class, () -> IlbmUtility.toBufferedImage(image));
    }

    // -------------------------------------------------------------------------
    // toPlanar
    // -------------------------------------------------------------------------

    @Test
    void toPlanar_roundTrip() {
        var ilbm  = buildTestImage();
        var bimg  = IlbmUtility.toBufferedImage(ilbm);
        var body2 = IlbmUtility.toPlanar(bimg, ilbm.bmhd().planes());

        assertArrayEquals(ilbm.body(), body2);
    }

    @Test
    void toPlanar_nonIndexed_throws() {
        var rgb = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        assertThrows(IffParseException.class, () -> IlbmUtility.toPlanar(rgb, 2));
    }

    // -------------------------------------------------------------------------
    // extractPalette
    // -------------------------------------------------------------------------

    @Test
    void extractPalette_nonIndexed_throws() {
        var rgb = new BufferedImage(4, 2, BufferedImage.TYPE_INT_RGB);
        assertThrows(IffParseException.class, () -> IlbmUtility.extractPalette(rgb));
    }

    @Test
    void extractPalette_returnsRgbValues() {
        var ilbm = buildTestImage();
        var bimg = IlbmUtility.toBufferedImage(ilbm);
        int[] extracted = IlbmUtility.extractPalette(bimg);

        assertEquals(ilbm.palette().length, extracted.length);
        assertEquals(0xFF0000, extracted[1]); // red
        assertEquals(0x00FF00, extracted[2]); // green
        assertEquals(0x0000FF, extracted[3]); // blue
    }
}
