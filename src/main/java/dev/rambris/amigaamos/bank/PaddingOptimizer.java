/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.function.ToIntFunction;

/**
 * Tries padding an image by 1..{@code maxPad} pixels to the right and to the bottom to see
 * whether a compressor produces smaller output for the larger image.
 *
 * <p>For Pac.Pic. this can pay off because the encoder only considers lump heights that evenly
 * divide the image height; growing a 200-line picture to e.g. 216 lines unlocks lump heights
 * 12 and 24. Padding pixels repeat the nearest edge pixel, which keeps the extra bytes
 * run-length friendly.
 *
 * <p>Width and height padding are each searched independently; if both improve, the
 * combination is also tried and kept only if it is the smallest.
 */
public class PaddingOptimizer {

    static boolean progress = true;

    private PaddingOptimizer() {}

    /**
     * @param pixels palette-index array {@code [y][x]} — not modified
     * @param maxPad largest padding to try in each direction
     * @param sizer  size oracle: compressed byte count for a given pixel array
     */
    public static Result optimize(int[][] pixels, int maxPad, ToIntFunction<int[][]> sizer) {
        int originalSize = sizer.applyAsInt(pixels);
        int bestW = 0, sizeW = originalSize;
        int bestH = 0, sizeH = originalSize;
        for (int d = 1; d <= maxPad; d++) {
            int s = sizer.applyAsInt(pad(pixels, d, 0));
            if (s < sizeW) { sizeW = s; bestW = d; }
            s = sizer.applyAsInt(pad(pixels, 0, d));
            if (s < sizeH) { sizeH = s; bestH = d; }
        }

        int padX = 0, padY = 0, best = originalSize;
        if (sizeW < best) { best = sizeW; padX = bestW; padY = 0; }
        if (sizeH < best) { best = sizeH; padX = 0; padY = bestH; }
        if (bestW > 0 && bestH > 0) {
            int s = sizer.applyAsInt(pad(pixels, bestW, bestH));
            if (s < best) { best = s; padX = bestW; padY = bestH; }
        }

        if (progress) {
            int w = pixels.length > 0 ? pixels[0].length : 0;
            if (padX == 0 && padY == 0) {
                System.out.printf("Padding: no improvement (%dx%d, %d bytes)%n", w, pixels.length, originalSize);
            } else {
                System.out.printf("Padding: %dx%d -> %dx%d. %d > %d (%.1f%%)%n",
                        w, pixels.length, w + padX, pixels.length + padY, originalSize, best,
                        100.0 * (originalSize - best) / originalSize);
            }
        }
        var out = (padX == 0 && padY == 0) ? PaletteOptimizer.deepCopy(pixels) : pad(pixels, padX, padY);
        return new Result(out, padX, padY, best);
    }

    /** Returns a copy of {@code pixels} grown by {@code dw}×{@code dh}, repeating edge pixels. */
    static int[][] pad(int[][] pixels, int dw, int dh) {
        int h = pixels.length;
        int w = h > 0 ? pixels[0].length : 0;
        var out = new int[h + dh][w + dw];
        for (int y = 0; y < h + dh; y++) {
            var src = pixels[Math.min(y, h - 1)];
            System.arraycopy(src, 0, out[y], 0, w);
            int edge = src[w - 1];
            for (int x = w; x < w + dw; x++) out[y][x] = edge;
        }
        return out;
    }

    /**
     * @param pixels         padded pixel array (a copy even when no padding was chosen)
     * @param padX           pixels added to the right
     * @param padY           lines added to the bottom
     * @param compressedSize compressed size of {@code pixels}
     */
    public record Result(int[][] pixels, int padX, int padY, int compressedSize) {}
}
