/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.awt.image.IndexColorModel;

/**
 * Helpers for AMOS Amiga 12-bit palette values ({@code 0x0RGB}).
 */
public final class AmigaPalette {

    private AmigaPalette() {
    }

    /** Parses {@code "#RGB"} or {@code "RGB"} to an AMOS colour word {@code 0x0RGB}. */
    public static int parseHexRgb(String color) {
        var hex = color.startsWith("#") ? color.substring(1) : color;
        return Integer.parseInt(hex, 16) & 0xFFF;
    }

    /** Formats an AMOS colour word {@code 0x0RGB} as {@code "#RGB"}. */
    public static String toHexRgb(int color) {
        return "#%03X".formatted(color & 0xFFF);
    }

    /** Converts one 4-bit AMOS channel to 8-bit RGB channel. */
    public static int to8BitChannel(int nibble) {
        return (nibble & 0xF) * 17;
    }

    /**
     * Builds an 8-bit {@link IndexColorModel} from an AMOS palette.
     *
     * @param palette   AMOS palette words ({@code 0x0RGB})
     * @param maxColors number of entries to expose in the color model
     */
    public static IndexColorModel buildIndexColorModel(int[] palette, int maxColors) {
        var reds = new byte[256];
        var greens = new byte[256];
        var blues = new byte[256];
        for (int i = 0; i < Math.min(maxColors, 256); i++) {
            var c = i < palette.length ? palette[i] : 0;
            reds[i] = (byte) to8BitChannel((c >> 8) & 0xF);
            greens[i] = (byte) to8BitChannel((c >> 4) & 0xF);
            blues[i] = (byte) to8BitChannel(c & 0xF);
        }
        return new IndexColorModel(8, Math.min(maxColors, 256), reds, greens, blues);
    }
}

