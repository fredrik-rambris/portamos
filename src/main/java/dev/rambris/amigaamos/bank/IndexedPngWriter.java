/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.iff.codec.BmhdChunk;
import dev.rambris.iff.codec.IlbmCodec;
import dev.rambris.iff.codec.IlbmImage;
import dev.rambris.iff.codec.IlbmOptions;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.InflaterInputStream;

/**
 * Writes 8-bit indexed-colour PNG and IFF ILBM files from Amiga pixel data,
 * without any AWT or ImageIO dependency (compatible with GraalVM native images).
 */
class IndexedPngWriter {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private IndexedPngWriter() {}

    // -------------------------------------------------------------------------
    // Read: PNG and IFF ILBM → PixelImage
    // -------------------------------------------------------------------------

    /**
     * Decoded indexed image: per-row palette indices, PLTE/CMAP colors, and dimensions.
     *
     * @param width     image width in pixels
     * @param height    image height in pixels
     * @param numColors number of palette entries
     * @param pixels    {@code pixels[y][x]} = palette index
     * @param palette24 palette as 24-bit {@code 0x00RRGGBB} values (length = numColors)
     */
    record PixelImage(int width, int height, int numColors, int[][] pixels, int[] palette24) {}

    /**
     * Reads an 8-bit indexed PNG or IFF ILBM image.
     * Format is auto-detected from the file extension ({@code .iff} / {@code .ilbm} → ILBM;
     * everything else → PNG).
     */
    static PixelImage readPixels(Path path) throws IOException {
        var name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".iff") || name.endsWith(".ilbm")) {
            return readIlbmPixels(path);
        }
        return readPngPixels(path);
    }

    private static PixelImage readPngPixels(Path path) throws IOException {
        var data = Files.readAllBytes(path);
        if (data.length < 8) throw new IOException("File too small to be PNG: " + path);
        for (int i = 0; i < PNG_SIGNATURE.length; i++) {
            if (data[i] != PNG_SIGNATURE[i]) throw new IOException("Not a PNG file: " + path);
        }

        int pos = 8;
        int width = 0, height = 0, bitDepth = 0, colorType = 0;
        int[] palette24 = new int[0];
        var idatBuf = new ByteArrayOutputStream();

        while (pos + 12 <= data.length) {
            int length = readInt32(data, pos); pos += 4;
            var type = new String(data, pos, 4, StandardCharsets.US_ASCII); pos += 4;
            switch (type) {
                case "IHDR" -> {
                    width     = readInt32(data, pos);
                    height    = readInt32(data, pos + 4);
                    bitDepth  = data[pos + 8] & 0xFF;
                    colorType = data[pos + 9] & 0xFF;
                }
                case "PLTE" -> {
                    int count = length / 3;
                    palette24 = new int[count];
                    for (int i = 0; i < count; i++) {
                        int r = data[pos + i * 3]     & 0xFF;
                        int g = data[pos + i * 3 + 1] & 0xFF;
                        int b = data[pos + i * 3 + 2] & 0xFF;
                        palette24[i] = (r << 16) | (g << 8) | b;
                    }
                }
                case "IDAT" -> idatBuf.write(data, pos, length);
                default -> {}
            }
            pos += length + 4; // skip chunk data + CRC
        }

        if (colorType != 3) throw new IOException(
                "Not an indexed-colour PNG (color type " + colorType + "): " + path);
        if (bitDepth != 1 && bitDepth != 2 && bitDepth != 4 && bitDepth != 8) throw new IOException(
                "Unsupported PNG bit depth " + bitDepth + ": " + path);

        byte[] raw;
        try (var iis = new InflaterInputStream(new ByteArrayInputStream(idatBuf.toByteArray()))) {
            raw = iis.readAllBytes();
        }

        int rowBytes = (width * bitDepth + 7) / 8;
        var pixels = new int[height][width];
        var prior  = new byte[rowBytes];
        int rawPos = 0;
        for (int y = 0; y < height; y++) {
            int filter = raw[rawPos++] & 0xFF;
            var row = new byte[rowBytes];
            System.arraycopy(raw, rawPos, row, 0, rowBytes);
            rawPos += rowBytes;
            applyFilter(filter, row, prior, rowBytes);
            if (bitDepth == 8) {
                for (int x = 0; x < width; x++) pixels[y][x] = row[x] & 0xFF;
            } else {
                int pxPerByte = 8 / bitDepth;
                int mask = (1 << bitDepth) - 1;
                for (int x = 0; x < width; x++) {
                    int shift = (pxPerByte - 1 - x % pxPerByte) * bitDepth;
                    pixels[y][x] = (row[x / pxPerByte] >> shift) & mask;
                }
            }
            prior = row.clone();
        }

        return new PixelImage(width, height, palette24.length, pixels, palette24);
    }

    private static PixelImage readIlbmPixels(Path path) {
        var image    = IlbmCodec.read(path);
        var bmhd     = image.bmhd();
        int width    = bmhd.width();
        int height   = bmhd.height();
        int planes   = bmhd.planes();
        int numColors = 1 << planes;
        int rowBytes  = ((width + 15) / 16) * 2;
        var body     = image.body();

        var pixels = new int[height][width];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int ci = 0;
                for (int p = 0; p < planes; p++) {
                    int off = y * planes * rowBytes + p * rowBytes + x / 8;
                    ci |= ((body[off] >> (7 - x % 8)) & 1) << p;
                }
                pixels[y][x] = ci;
            }
        }

        var pal = image.palette() != null ? image.palette() : new int[0];
        return new PixelImage(width, height, numColors, pixels, pal);
    }

    private static void applyFilter(int filter, byte[] row, byte[] prior, int width) {
        switch (filter) {
            case 1 -> { for (int x = 1; x < width; x++) row[x] += row[x - 1]; }
            case 2 -> { for (int x = 0; x < width; x++) row[x] += prior[x]; }
            case 3 -> {
                for (int x = 0; x < width; x++) {
                    int a = x > 0 ? row[x - 1] & 0xFF : 0;
                    row[x] = (byte)(row[x] + (a + (prior[x] & 0xFF)) / 2);
                }
            }
            case 4 -> {
                for (int x = 0; x < width; x++) {
                    int a = x > 0 ? row[x - 1] & 0xFF : 0;
                    int b = prior[x] & 0xFF;
                    int c = x > 0 ? prior[x - 1] & 0xFF : 0;
                    row[x] = (byte)(row[x] + paeth(a, b, c));
                }
            }
            // case 0 (None): no change
        }
    }

    private static int paeth(int a, int b, int c) {
        int p = a + b - c, pa = Math.abs(p - a), pb = Math.abs(p - b), pc = Math.abs(p - c);
        return pa <= pb && pa <= pc ? a : pb <= pc ? b : c;
    }

    private static int readInt32(byte[] d, int off) {
        return ((d[off] & 0xFF) << 24) | ((d[off+1] & 0xFF) << 16)
             | ((d[off+2] & 0xFF) << 8) |  (d[off+3] & 0xFF);
    }

    /**
     * Writes an 8-bit indexed-colour PNG from Amiga pixel data.
     *
     * @param amigaPalette Amiga colour words ({@code 0x0RGB})
     * @param numColors    number of palette entries to embed in the PNG
     * @param pixels       {@code pixels[y][x]} = palette index
     * @param width        image width in pixels
     * @param height       image height in pixels
     * @param dest         output file path
     */
    static void writePng(int[] amigaPalette, int numColors,
                         int[][] pixels, int width, int height,
                         Path dest) throws IOException {
        int n = Math.min(numColors, 256);
        var palette24 = toRgb24(amigaPalette, n);

        var out = new ByteArrayOutputStream();
        out.write(PNG_SIGNATURE);

        // IHDR: width(4) height(4) bitDepth(1) colorType(1) compression(1) filter(1) interlace(1)
        var ihdr = new byte[13];
        putInt(ihdr, 0, width);
        putInt(ihdr, 4, height);
        ihdr[8] = 8; // bit depth: 8 bits per sample
        ihdr[9] = 3; // color type: indexed (palette)
        // bytes 10-12 remain 0: deflate compression, adaptive filtering, no interlace
        writeChunk(out, "IHDR", ihdr);

        // PLTE: 3 bytes per entry (R, G, B)
        var plte = new byte[n * 3];
        for (int i = 0; i < n; i++) {
            plte[i * 3]     = (byte)((palette24[i] >> 16) & 0xFF);
            plte[i * 3 + 1] = (byte)((palette24[i] >>  8) & 0xFF);
            plte[i * 3 + 2] = (byte)( palette24[i]        & 0xFF);
        }
        writeChunk(out, "PLTE", plte);

        // IDAT: filter byte (0 = None) per row + raw pixel indices, deflate-compressed
        var raw = new ByteArrayOutputStream(height * (width + 1));
        for (int y = 0; y < height; y++) {
            raw.write(0); // filter: None
            for (int x = 0; x < width; x++) {
                raw.write(pixels[y][x] & 0xFF);
            }
        }
        writeChunk(out, "IDAT", deflate(raw.toByteArray()));

        writeChunk(out, "IEND", new byte[0]);
        Files.write(dest, out.toByteArray());
    }

    /**
     * Writes an IFF ILBM from Amiga pixel data using ByteRun1 compression.
     *
     * @param amigaPalette Amiga colour words ({@code 0x0RGB})
     * @param planes       number of bitplanes (palette size = 1 &lt;&lt; planes)
     * @param pixels       {@code pixels[y][x]} = palette index
     * @param width        image width in pixels
     * @param height       image height in pixels
     * @param dest         output file path
     */
    static void writeIlbm(int[] amigaPalette, int planes,
                           int[][] pixels, int width, int height,
                           Path dest) {
        int numColors = 1 << planes;
        var palette24 = toRgb24(amigaPalette, Math.min(numColors, amigaPalette.length));
        var body = toInterleaved(pixels, width, height, planes);
        var bmhd = new BmhdChunk(
                width, height, 0, 0,
                planes, 0, BmhdChunk.COMPRESSION_NONE, 0,
                10, 11, width, height);
        IlbmCodec.write(new IlbmImage(bmhd, palette24, 0, body), dest, IlbmOptions.COMPRESSION_BYTERUN1);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /** Converts Amiga {@code 0x0RGB} palette words to 24-bit {@code 0x00RRGGBB} values. */
    private static int[] toRgb24(int[] amigaPalette, int count) {
        var result = new int[count];
        for (int i = 0; i < count; i++) {
            var c = i < amigaPalette.length ? amigaPalette[i] : 0;
            var r = AmigaPalette.to8BitChannel((c >> 8) & 0xF);
            var g = AmigaPalette.to8BitChannel((c >> 4) & 0xF);
            var b = AmigaPalette.to8BitChannel(c & 0xF);
            result[i] = (r << 16) | (g << 8) | b;
        }
        return result;
    }

    /**
     * Converts indexed pixel data to Amiga interleaved planar format (ILBM BODY).
     *
     * <p>For each row y, for each plane p, writes {@code rowBytes = ((width+15)/16)*2}
     * bytes where bit {@code (7-bit)} of byte b encodes bit p of pixel {@code pixels[y][b*8+bit]}.
     */
    private static byte[] toInterleaved(int[][] pixels, int width, int height, int planes) {
        int rowBytes = ((width + 15) / 16) * 2;
        var body = new byte[height * planes * rowBytes];
        int pos = 0;
        for (int y = 0; y < height; y++) {
            for (int p = 0; p < planes; p++) {
                for (int b = 0; b < rowBytes; b++) {
                    int val = 0;
                    for (int bit = 0; bit < 8; bit++) {
                        int x = b * 8 + bit;
                        if (x < width && ((pixels[y][x] >> p) & 1) == 1) {
                            val |= 0x80 >> bit;
                        }
                    }
                    body[pos++] = (byte) val;
                }
            }
        }
        return body;
    }

    private static byte[] deflate(byte[] data) throws IOException {
        var deflater = new Deflater(Deflater.BEST_COMPRESSION);
        try {
            var out = new ByteArrayOutputStream(data.length / 2 + 1);
            try (var dos = new DeflaterOutputStream(out, deflater)) {
                dos.write(data);
            }
            return out.toByteArray();
        } finally {
            deflater.end();
        }
    }

    private static void writeChunk(ByteArrayOutputStream out, String type, byte[] data) {
        var tb = type.getBytes(StandardCharsets.US_ASCII);
        putInt32(out, data.length);
        out.write(tb, 0, 4);
        if (data.length > 0) out.write(data, 0, data.length);
        var crc = new CRC32();
        crc.update(tb);
        if (data.length > 0) crc.update(data);
        putInt32(out, (int) crc.getValue());
    }

    private static void putInt32(ByteArrayOutputStream out, int v) {
        out.write((v >> 24) & 0xFF);
        out.write((v >> 16) & 0xFF);
        out.write((v >>  8) & 0xFF);
        out.write( v        & 0xFF);
    }

    private static void putInt(byte[] buf, int off, int v) {
        buf[off]     = (byte)((v >> 24) & 0xFF);
        buf[off + 1] = (byte)((v >> 16) & 0xFF);
        buf[off + 2] = (byte)((v >>  8) & 0xFF);
        buf[off + 3] = (byte)( v        & 0xFF);
    }
}
