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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.CRC32;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Writes 8-bit indexed-colour PNG and IFF ILBM files from Amiga pixel data,
 * without any AWT or ImageIO dependency (compatible with GraalVM native images).
 */
class IndexedPngWriter {

    private static final byte[] PNG_SIGNATURE = {
        (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
    };

    private IndexedPngWriter() {}

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
