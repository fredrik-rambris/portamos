/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.imageio;

import dev.rambris.iff.codec.BmhdChunk;
import dev.rambris.iff.codec.IlbmCodec;
import dev.rambris.iff.codec.IlbmImage;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

class IlbmImageIOTest {

    // -------------------------------------------------------------------------
    // Test fixtures
    // -------------------------------------------------------------------------

    /** 4×2, 2-plane ILBM with a 4-entry palette. */
    private static byte[] makeTestIlbm() {
        var bmhd = new BmhdChunk(4, 2, 0, 0, 2, 0, BmhdChunk.COMPRESSION_NONE,
                0, 1, 1, 4, 2);
        int[] palette = {0x000000, 0xFF0000, 0x00FF00, 0x0000FF};
        byte[] body = new byte[8]; // all pixel-index 0 (black)
        return IlbmCodec.write(new IlbmImage(bmhd, palette, 0, body));
    }

    /** 4-colour indexed BufferedImage (4×2). */
    private static BufferedImage makeIndexedImage() {
        byte[] r = {0, (byte) 255, 0,   0};
        byte[] g = {0, 0,   (byte) 255, 0};
        byte[] b = {0, 0,   0,   (byte) 255};
        var cm  = new IndexColorModel(2, 4, r, g, b);
        var img = new BufferedImage(4, 2, BufferedImage.TYPE_BYTE_INDEXED, cm);
        // leave all pixels at index 0 (black)
        return img;
    }

    // -------------------------------------------------------------------------
    // Register SPIs once for all tests
    // -------------------------------------------------------------------------

    @BeforeAll
    static void registerSpis() {
        ImageIO.scanForPlugins();
    }

    // -------------------------------------------------------------------------
    // IlbmImageReaderSpi
    // -------------------------------------------------------------------------

    @Test
    void readerSpi_canDecodeInput_true_forIlbm() throws IOException {
        var spi = new IlbmImageReaderSpi();
        byte[] ilbm = makeTestIlbm();
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            assertTrue(spi.canDecodeInput(stream));
        }
    }

    @Test
    void readerSpi_canDecodeInput_false_forNonIlbm() throws IOException {
        var spi = new IlbmImageReaderSpi();
        byte[] garbage = {0x00, 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B};
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(garbage))) {
            assertFalse(spi.canDecodeInput(garbage));          // non-stream type
            assertFalse(spi.canDecodeInput(stream));           // wrong magic
        }
    }

    @Test
    void readerSpi_canDecodeInput_false_forSvx8() throws IOException {
        // "FORM" + size + "8SVX" — valid IFF but not ILBM
        byte[] svx8 = new byte[]{
            'F','O','R','M', 0,0,0,4, '8','S','V','X'
        };
        var spi = new IlbmImageReaderSpi();
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(svx8))) {
            assertFalse(spi.canDecodeInput(stream));
        }
    }

    @Test
    void readerSpi_canDecodeInput_doesNotConsumeStream() throws IOException {
        var spi = new IlbmImageReaderSpi();
        byte[] ilbm = makeTestIlbm();
        try (ImageInputStream stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            spi.canDecodeInput(stream); // call once
            spi.canDecodeInput(stream); // call again — stream position must be reset
            assertTrue(spi.canDecodeInput(stream)); // third call still works
        }
    }

    // -------------------------------------------------------------------------
    // IlbmImageWriterSpi
    // -------------------------------------------------------------------------

    @Test
    void writerSpi_canEncodeImage_true_forIndexed() {
        var spi  = new IlbmImageWriterSpi();
        var spec = javax.imageio.ImageTypeSpecifier.createFromRenderedImage(makeIndexedImage());
        assertTrue(spi.canEncodeImage(spec));
    }

    @Test
    void writerSpi_canEncodeImage_false_forRgb() {
        var spi  = new IlbmImageWriterSpi();
        var spec = javax.imageio.ImageTypeSpecifier.createFromBufferedImageType(BufferedImage.TYPE_INT_RGB);
        assertFalse(spi.canEncodeImage(spec));
    }

    // -------------------------------------------------------------------------
    // IlbmImageReader — direct usage
    // -------------------------------------------------------------------------

    @Test
    void reader_read_returnsBitmapWithCorrectDimensions() throws IOException {
        byte[] ilbm = makeTestIlbm();
        var spi    = new IlbmImageReaderSpi();
        var reader = spi.createReaderInstance(null);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            reader.setInput(stream);
            assertEquals(4, reader.getWidth(0));
            assertEquals(2, reader.getHeight(0));
            assertEquals(1, reader.getNumImages(false));
        }
    }

    @Test
    void reader_read_returnsIndexedImage() throws IOException {
        byte[] ilbm = makeTestIlbm();
        var spi    = new IlbmImageReaderSpi();
        var reader = spi.createReaderInstance(null);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            reader.setInput(stream);
            BufferedImage img = reader.read(0, null);
            assertNotNull(img);
            assertTrue(img.getColorModel() instanceof IndexColorModel,
                    "Expected IndexColorModel, got " + img.getColorModel().getClass().getSimpleName());
            assertEquals(4, img.getWidth());
            assertEquals(2, img.getHeight());
        }
    }

    @Test
    void reader_getImageTypes_returnsOneType() throws IOException {
        byte[] ilbm = makeTestIlbm();
        var reader = new IlbmImageReaderSpi().createReaderInstance(null);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            reader.setInput(stream);
            var types = reader.getImageTypes(0);
            assertTrue(types.hasNext());
        }
    }

    @Test
    void reader_indexOutOfBounds_throws() throws IOException {
        byte[] ilbm = makeTestIlbm();
        var reader = new IlbmImageReaderSpi().createReaderInstance(null);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(ilbm))) {
            reader.setInput(stream);
            assertThrows(IndexOutOfBoundsException.class, () -> reader.getWidth(1));
        }
    }

    // -------------------------------------------------------------------------
    // IlbmImageWriter — direct usage
    // -------------------------------------------------------------------------

    @Test
    void writer_write_producesReadableIlbm() throws IOException {
        var image = makeIndexedImage();
        var baos  = new ByteArrayOutputStream();

        var writer = new IlbmImageWriterSpi().createWriterInstance(null);
        try (ImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), null);
        }

        // Round-trip: read back and verify dimensions
        var reader = new IlbmImageReaderSpi().createReaderInstance(null);
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(baos.toByteArray()))) {
            reader.setInput(stream);
            BufferedImage decoded = reader.read(0, null);
            assertEquals(image.getWidth(),  decoded.getWidth());
            assertEquals(image.getHeight(), decoded.getHeight());
        }
    }

    @Test
    void writer_write_byteRun1_param_producesValidOutput() throws IOException {
        var image = makeIndexedImage();
        var baos  = new ByteArrayOutputStream();

        var writerSpi = new IlbmImageWriterSpi();
        var writer    = writerSpi.createWriterInstance(null);

        var param = (IlbmWriteParam) writer.getDefaultWriteParam();
        param.setCompressionType(IlbmWriteParam.COMPRESSION_BYTERUN1);

        try (ImageOutputStream out = new MemoryCacheImageOutputStream(baos)) {
            writer.setOutput(out);
            writer.write(null, new IIOImage(image, null, null), param);
        }

        // BODY in result should be ByteRun1-compressed; verify via full codec read
        var decoded = IlbmCodec.read(baos.toByteArray());
        assertEquals(BmhdChunk.COMPRESSION_BYTERUN1, decoded.bmhd().compression());
        assertEquals(4, decoded.bmhd().width());
        assertEquals(2, decoded.bmhd().height());
    }

    @Test
    void writer_defaultWriteParam_isCompressionNone() {
        var writer = new IlbmImageWriterSpi().createWriterInstance(null);
        var param  = (IlbmWriteParam) writer.getDefaultWriteParam();
        assertEquals(IlbmWriteParam.COMPRESSION_NONE, param.getCompressionType());
        assertEquals(ImageWriteParam.MODE_EXPLICIT, param.getCompressionMode());
    }

    @Test
    void writer_availableCompressionTypes_containsBothTypes() {
        var writer = new IlbmImageWriterSpi().createWriterInstance(null);
        var param  = (IlbmWriteParam) writer.getDefaultWriteParam();
        var types  = Arrays.asList(param.getCompressionTypes());
        assertTrue(types.contains(IlbmWriteParam.COMPRESSION_NONE));
        assertTrue(types.contains(IlbmWriteParam.COMPRESSION_BYTERUN1));
    }

    // -------------------------------------------------------------------------
    // Full round-trip via ImageIO API
    // -------------------------------------------------------------------------

    @Test
    void imageIO_readWrite_roundTrip() throws IOException {
        byte[] original = makeTestIlbm();

        // Read via ImageIO
        BufferedImage img;
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(original))) {
            var readers = ImageIO.getImageReaders(stream);
            assertTrue(readers.hasNext(), "No ImageIO reader found for ILBM");
            var reader = readers.next();
            reader.setInput(stream);
            img = reader.read(0, null);
        }

        // Write via ImageIO
        var baos = new ByteArrayOutputStream();
        try (var stream = new MemoryCacheImageOutputStream(baos)) {
            var writers = ImageIO.getImageWritersByFormatName("ILBM");
            assertTrue(writers.hasNext(), "No ImageIO writer found for ILBM");
            var writer = writers.next();
            writer.setOutput(stream);
            writer.write(null, new IIOImage(img, null, null), null);
        }

        // Re-read the written bytes and compare dimensions
        var reread = IlbmCodec.read(baos.toByteArray());
        assertEquals(4, reread.bmhd().width());
        assertEquals(2, reread.bmhd().height());
        assertEquals(4, reread.palette().length);
    }

    @Test
    void imageIO_write_preservesPaletteColours() throws IOException {
        var image = makeIndexedImage();
        var baos  = new ByteArrayOutputStream();

        try (var stream = new MemoryCacheImageOutputStream(baos)) {
            var writers = ImageIO.getImageWritersByFormatName("ILBM");
            var writer  = writers.next();
            writer.setOutput(stream);
            writer.write(null, new IIOImage(image, null, null), null);
        }

        var decoded = IlbmCodec.read(baos.toByteArray());
        assertNotNull(decoded.palette());
        assertEquals(4, decoded.palette().length);
        assertEquals(0xFF0000, decoded.palette()[1]); // red
        assertEquals(0x00FF00, decoded.palette()[2]); // green
        assertEquals(0x0000FF, decoded.palette()[3]); // blue
    }
}
