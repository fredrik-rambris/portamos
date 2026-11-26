/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

/**
 * Pac.Pic.-specific convenience facade over the generic {@link PaletteOptimizer}, using
 * {@link PacPicEncoder#compress} as the size oracle.
 */
public class PacPicPaletteOptimizer {

    private PacPicPaletteOptimizer() {}

    /**
     * @param pixels     palette-index array {@code [y][x]} — not modified
     * @param numColors  number of palette entries that may be swapped (usually 1 &lt;&lt; planes)
     * @param srcX       forwarded to {@link PacPicEncoder#compress}
     * @param srcY       forwarded to {@link PacPicEncoder#compress}
     * @param planes     forwarded to {@link PacPicEncoder#compress}
     * @return optimized result (may be identical to input if no improvement found)
     */
    public static PaletteOptimizer.Result optimize(int[][] pixels, int numColors,
                                                   int srcX, int srcY, int planes) {
        return PaletteOptimizer.optimize(pixels, numColors,
                PaletteOptimizer.pacPicSize(srcX, srcY, planes));
    }

    /** @see PaletteOptimizer#applyPermutation(int[], int[]) */
    public static int[] applyPermutation(int[] palette, int[] permutation) {
        return PaletteOptimizer.applyPermutation(palette, permutation);
    }
}
