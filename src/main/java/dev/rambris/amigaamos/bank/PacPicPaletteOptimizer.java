/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

/**
 * Greedy pairwise-swap palette optimizer for Pac.Pic. compression.
 *
 * <p>Uses {@link PacPicEncoder#compress} as the oracle: tries every (i,j) palette index swap,
 * keeps any swap that strictly reduces the compressed byte count, and restarts until a full pass
 * finds no improvement.
 *
 * <p>The returned permutation {@code perm[newIndex] = oldIndex} lets callers reorder any
 * parallel palette array (12-bit AMOS palette, 24-bit PNG palette, etc.) in one step.
 */
public class PacPicPaletteOptimizer {

    /**
     * @param pixels     palette-index array {@code [y][x]} — not modified
     * @param numColors  number of palette entries that may be swapped (usually 1 &lt;&lt; planes)
     * @param srcX       forwarded to {@link PacPicEncoder#compress}
     * @param srcY       forwarded to {@link PacPicEncoder#compress}
     * @param planes     forwarded to {@link PacPicEncoder#compress}
     * @return optimized result (may be identical to input if no improvement found)
     */
    public static Result optimize(int[][] pixels, int numColors,
                                  int srcX, int srcY, int planes) {
        // Work on copies so input is never mutated
        var cur = deepCopy(pixels);
        var perm = identityPerm(numColors);  // perm[newIdx] = originalIdx

        int bestSize = PacPicEncoder.compress(cur, srcX, srcY, planes).length;

        boolean improved = true;
        while (improved) {
            improved = false;
            outer:
            for (int i = 0; i < numColors - 1; i++) {
                for (int j = i + 1; j < numColors; j++) {
                    swapIndices(cur, i, j);
                    int size = PacPicEncoder.compress(cur, srcX, srcY, planes).length;
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

        return new Result(cur, perm, bestSize);
    }

    /**
     * Applies {@code permutation} (as returned by {@link #optimize}) to any parallel palette
     * array. {@code permutation[newIndex] = oldIndex}, so:
     * <pre>
     *   newPalette[i] = oldPalette[permutation[i]]
     * </pre>
     *
     * @param palette     source palette (any numeric array of length ≥ numColors)
     * @param permutation permutation as returned from {@link Result#permutation()}
     * @return new palette with entries reordered to match the optimized pixel array
     */
    public static int[] applyPermutation(int[] palette, int[] permutation) {
        var result = new int[palette.length];
        for (int i = 0; i < permutation.length && i < palette.length; i++) {
            result[i] = palette[permutation[i]];
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

    private static int[][] deepCopy(int[][] src) {
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
