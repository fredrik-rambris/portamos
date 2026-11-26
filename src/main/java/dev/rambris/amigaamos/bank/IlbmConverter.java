/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.iff.codec.AmigaScreenMode;
import dev.rambris.iff.codec.BmhdChunk;
import dev.rambris.iff.codec.IlbmCodec;
import dev.rambris.iff.codec.IlbmImage;
import dev.rambris.iff.codec.IlbmOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Converts any supported indexed image (8-bit indexed PNG or IFF ILBM) to a ByteRun1-compressed
 * IFF ILBM, optionally running the {@link PaletteOptimizer} first to find the colour ordering that
 * gives the smallest {@code BODY}.
 *
 * <p>When the source is an ILBM its {@code CAMG} display mode and transparent colour are carried
 * over (the transparent colour index is remapped through the optimizer's permutation).
 * HAM and Extra-HalfBrite sources are rejected, since their bitplane values are not plain
 * palette indices.
 */
public class IlbmConverter {

    /** BMHD masking values (EA IFF 85 ILBM spec). */
    private static final int MASKING_NONE = 0;
    private static final int MASKING_TRANSPARENT_COLOR = 2;

    private boolean optimize = false;
    private int planes = 0;

    /** Enable or disable palette optimisation before compression. */
    public IlbmConverter withOptimize(boolean value) {
        this.optimize = value;
        return this;
    }

    /** Force the number of bitplanes; {@code 0} (default) derives it from the palette size. */
    public IlbmConverter withPlanes(int value) {
        this.planes = value;
        return this;
    }

    /** Summary of a conversion. */
    public record Result(int width, int height, int planes, int bodySize, byte[] ilbm) {}

    /** Converts {@code input} and writes the ILBM file to {@code output}. */
    public Result convert(Path input, Path output) throws IOException {
        var result = convert(input);
        Files.write(output, result.ilbm());
        return result;
    }

    /** Converts {@code input} to ILBM bytes. */
    public Result convert(Path input) throws IOException {
        int camgMode = 0;
        int transparent = 0;
        int masking = MASKING_NONE;
        var lower = input.getFileName().toString().toLowerCase();
        if (lower.endsWith(".iff") || lower.endsWith(".ilbm")) {
            var src = IlbmCodec.read(input);
            camgMode = src.camgMode();
            if (AmigaScreenMode.isHam(camgMode)) {
                throw new NotSupportedException("ILBM conversion of HAM images is not supported: " + input);
            }
            if (AmigaScreenMode.isEhb(camgMode)) {
                throw new NotSupportedException(
                        "ILBM conversion of Extra-HalfBrite images is not supported: " + input);
            }
            if (src.bmhd().masking() == MASKING_TRANSPARENT_COLOR) {
                masking = MASKING_TRANSPARENT_COLOR;
                transparent = src.bmhd().transparentColor();
            }
        }

        var image = IndexedPngWriter.readPixels(input);
        int width = image.width();
        int height = image.height();
        int nPlanes = planes > 0 ? planes : planesFor(Math.max(image.numColors(), maxIndex(image.pixels()) + 1));
        int numColors = 1 << nPlanes;
        if (maxIndex(image.pixels()) >= numColors) {
            throw new IOException("Image uses colour index " + maxIndex(image.pixels())
                    + " which does not fit in " + nPlanes + " bitplanes: " + input);
        }

        // Palette padded/truncated to exactly 1 << planes entries
        var palette = new int[numColors];
        var src = image.palette24() != null ? image.palette24() : new int[0];
        System.arraycopy(src, 0, palette, 0, Math.min(src.length, numColors));

        var pixels = image.pixels();
        if (optimize) {
            var result = PaletteOptimizer.optimize(pixels, numColors, PaletteOptimizer.ilbmSize(width, nPlanes));
            pixels = result.pixels();
            palette = PaletteOptimizer.applyPermutation(palette, result.permutation());
            transparent = indexOf(result.permutation(), transparent);
        }

        var body = IndexedPngWriter.toInterleaved(pixels, width, height, nPlanes);
        var bmhd = new BmhdChunk(width, height, 0, 0, nPlanes, masking,
                BmhdChunk.COMPRESSION_NONE, transparent, 10, 11, width, height);
        var ilbm = IlbmCodec.write(new IlbmImage(bmhd, palette, camgMode, body), IlbmOptions.COMPRESSION_BYTERUN1);
        int bodySize = IlbmCodec.compressByteRun1(body, bmhd).length;
        return new Result(width, height, nPlanes, bodySize, ilbm);
    }

    /** ByteRun1-compressed ILBM {@code BODY} bytes for a palette-index pixel array. */
    static byte[] compressedBody(int[][] pixels, int width, int planes) {
        int height = pixels.length;
        var body = IndexedPngWriter.toInterleaved(pixels, width, height, planes);
        var bmhd = new BmhdChunk(width, height, 0, 0, planes, MASKING_NONE,
                BmhdChunk.COMPRESSION_NONE, 0, 10, 11, width, height);
        return IlbmCodec.compressByteRun1(body, bmhd);
    }

    private static int maxIndex(int[][] pixels) {
        int max = 0;
        for (var row : pixels) for (int v : row) if (v > max) max = v;
        return max;
    }

    private static int indexOf(int[] perm, int value) {
        for (int i = 0; i < perm.length; i++) if (perm[i] == value) return i;
        return value;
    }

    private static int planesFor(int nColors) {
        var p = 0;
        while ((1 << p) < nColors) p++;
        return Math.max(p, 1);
    }
}
