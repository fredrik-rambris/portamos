/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

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

    /** Converts a 24-bit {@code 0x00RRGGBB} colour to an AMOS 12-bit {@code 0x0RGB} word (upper nibble of each channel). */
    public static int from24Bit(int rgb24) {
        return ((rgb24 >> 16 & 0xF0) << 4) | (rgb24 >> 8 & 0xF0) | (rgb24 >> 4 & 0x0F);
    }

    /** Returns true if every entry in the palette is zero. */
    public static boolean isBlank(int[] palette) {
        for (int c : palette) if (c != 0) return false;
        return true;
    }

}

