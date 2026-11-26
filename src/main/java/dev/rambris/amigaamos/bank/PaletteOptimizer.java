/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.function.ToIntFunction;

/**
 * Greedy pairwise-swap palette optimizer for any compressor of indexed images.
 *
 * <p>The compressor is supplied as a <em>size oracle</em>: a function mapping a palette-index
 * pixel array {@code [y][x]} to its compressed byte count. The optimizer tries every (i,j) palette
 * index swap, keeps any swap that strictly reduces the compressed size, and restarts until a full
 * pass finds no improvement.
 *
 * <p>Because both Pac.Pic. and ILBM ByteRun1 are run-length schemes over bitplane bytes, the
 * ordering of the palette determines which bitplanes are uniform and thus how well the runs
 * compress. Ready-made oracles: {@link #pacPicSize(int, int, int)} and
 * {@link #ilbmSize(int, int)}.
 *
 * <p>The returned permutation {@code perm[newIndex] = oldIndex} lets callers reorder any
 * parallel palette array (12-bit AMOS palette, 24-bit PNG/ILBM palette, etc.) in one step.
 */
public class PaletteOptimizer {

    static boolean progress = true;

    private PaletteOptimizer() {}

    /**
     * @param pixels     palette-index array {@code [y][x]} — not modified
     * @param numColors  number of palette entries that may be swapped (usually 1 &lt;&lt; planes)
     * @param sizer      size oracle: compressed byte count for a given pixel array
     * @return optimized result (may be identical to input if no improvement found)
     */
    public static Result optimize(int[][] pixels, int numColors, ToIntFunction<int[][]> sizer) {
        // Work on copies so input is never mutated
        var cur = deepCopy(pixels);
        var perm = identityPerm(numColors);  // perm[newIdx] = originalIdx

        if (progress) {
            System.out.print("Optimizing palette ");
            System.out.flush();
        }
        var bestSize = sizer.applyAsInt(cur);
        var originalSize = bestSize;

        boolean improved = true;
        while (improved) {
            improved = false;
            if (progress) {
                System.out.print(".");
                System.out.flush();
            }
            outer:
            for (int i = 0; i < numColors - 1; i++) {
                for (int j = i + 1; j < numColors; j++) {
                    swapIndices(cur, i, j);
                    int size = sizer.applyAsInt(cur);
                    if (size < bestSize) {
                        bestSize = size;
                        int tmp = perm[i]; perm[i] = perm[j]; perm[j] = tmp;
                        improved = true;
                        break outer;
                    }
                    swapIndices(cur, i, j);
                }
            }
        }

        if (progress) {
            var gain = 100.0 * (originalSize - bestSize) / (float) originalSize;
            System.out.printf("%nOptimized. %d > %d (%.1f%%)%n", originalSize, bestSize, gain);
        }
        return new Result(cur, perm, bestSize);
    }

    /** Size oracle for Pac.Pic. compression ({@link PacPicEncoder#compress}). */
    public static ToIntFunction<int[][]> pacPicSize(int srcX, int srcY, int planes) {
        return p -> PacPicEncoder.compress(p, srcX, srcY, planes).length;
    }

    /** Size oracle for ILBM ByteRun1-compressed {@code BODY} data. */
    public static ToIntFunction<int[][]> ilbmSize(int width, int planes) {
        return p -> IlbmConverter.compressedBody(p, width, planes).length;
    }

    /**
     * Applies {@code permutation} (as returned by {@link #optimize}) to any parallel palette
     * array. {@code permutation[newIndex] = oldIndex}, so:
     * <pre>
     *   newPalette[i] = oldPalette[permutation[i]]
     * </pre>
     * Entries the permutation refers to beyond the end of {@code palette} are treated as 0.
     *
     * @param palette     source palette (any numeric array)
     * @param permutation permutation as returned from {@link Result#permutation()}
     * @return new palette with entries reordered to match the optimized pixel array
     */
    public static int[] applyPermutation(int[] palette, int[] permutation) {
        var result = new int[palette.length];
        for (int i = 0; i < permutation.length && i < palette.length; i++) {
            int src = permutation[i];
            result[i] = src < palette.length ? palette[src] : 0;
        }
        // copy any tail entries not covered by permutation
        for (int i = permutation.length; i < palette.length; i++) {
            result[i] = palette[i];
        }
        return result;
    }

    // -------------------------------------------------------------------------

    public record Result(int[][] pixels, int[] permutation, int compressedSize) {}

    // -------------------------------------------------------------------------

    private static void swapIndices(int[][] pixels, int a, int b) {
        for (var row : pixels) {
            for (int x = 0; x < row.length; x++) {
                if      (row[x] == a) row[x] = b;
                else if (row[x] == b) row[x] = a;
            }
        }
    }

    static int[][] deepCopy(int[][] src) {
        var dst = new int[src.length][];
        for (int y = 0; y < src.length; y++) dst[y] = src[y].clone();
        return dst;
    }

    private static int[] identityPerm(int n) {
        var p = new int[n];
        for (int i = 0; i < n; i++) p[i] = i;
        return p;
    }
}
