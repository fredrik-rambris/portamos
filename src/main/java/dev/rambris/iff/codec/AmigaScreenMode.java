/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

/**
 * Amiga OCS/ECS viewport mode constants and pixel-aspect helpers for the ILBM {@code CAMG} chunk.
 *
 * <p>The {@code CAMG} chunk stores the Amiga hardware viewport mode. Only the two low-order
 * mode bits are relevant here; upper bits carry ECS/AGA extended display IDs that this
 * library ignores (they are preserved on round-trip but not interpreted).
 *
 * <h3>Common OCS/ECS modes</h3>
 * <pre>
 *  Preset           CAMG value   Width    NTSC height   PAL height
 *  ─────────────── ──────────── ──────── ───────────── ────────────
 *  {@link #LORES}          0x0000       320       200           256
 *  {@link #LORES_LACE}     0x0004       320       400           512
 *  {@link #HIRES}          0x8000       640       200           256
 *  {@link #HIRES_LACE}     0x8004       640       400           512
 * </pre>
 *
 * <h3>Pixel aspect (BMHD {@code xAspect:yAspect} — pixel width:height)</h3>
 * <pre>
 *  Mode              xAspect   yAspect   Notes
 *  ─────────────── ───────── ───────── ──────────────────────────────────────
 *  LORES             1         1         square pixels  (10:11 accurate PAL)
 *  LORES+LACE        1         1         square pixels
 *  HIRES             1         2         tall pixels: half-width vs. lores
 *  HIRES+LACE        1         1         square pixels: lacing restores y density
 * </pre>
 * <p>The simplified values above match what Commodore tools (e.g. AMOS) write for
 * low-resolution images ({@code 1:1}), and are consistent with the 22:44 (= 1:2) ratio
 * found in real HIRES ILBM files.
 */
public final class AmigaScreenMode {

    // -------------------------------------------------------------------------
    // Viewport mode bits (BPLCON0 / lower word of ViewPort mode)
    // -------------------------------------------------------------------------

    /** High-resolution mode: 640 pixels wide. */
    public static final int HIRES_BIT = 0x8000;
    /** Interlaced mode: doubles the number of scan lines. */
    public static final int LACE_BIT  = 0x0004;
    /** Hold-And-Modify colour mode (6-bit colour palette expansion). */
    public static final int HAM_BIT   = 0x0800;
    /** Extra-HalfBrite mode (64 colours: 32 + 32 half-bright). */
    public static final int EHB_BIT   = 0x0080;

    // -------------------------------------------------------------------------
    // Common screen-mode presets
    // -------------------------------------------------------------------------

    /** Low-resolution, non-interlaced: 320×200 (NTSC) or 320×256 (PAL). */
    public static final int LORES      = 0x0000;
    /** Low-resolution interlaced: 320×400 (NTSC) or 320×512 (PAL). */
    public static final int LORES_LACE = LACE_BIT;
    /** High-resolution, non-interlaced: 640×200 (NTSC) or 640×256 (PAL). */
    public static final int HIRES      = HIRES_BIT;
    /** High-resolution interlaced: 640×400 (NTSC) or 640×512 (PAL). */
    public static final int HIRES_LACE = HIRES_BIT | LACE_BIT;

    private AmigaScreenMode() {}

    // -------------------------------------------------------------------------
    // Mode queries
    // -------------------------------------------------------------------------

    /** Returns {@code true} if the mode has the high-resolution bit set (640 pixels wide). */
    public static boolean isHires(int mode) { return (mode & HIRES_BIT) != 0; }

    /** Returns {@code true} if the mode has the interlace bit set. */
    public static boolean isLace(int mode)  { return (mode & LACE_BIT)  != 0; }

    /** Returns {@code true} if the mode has the HAM bit set. */
    public static boolean isHam(int mode)   { return (mode & HAM_BIT)   != 0; }

    /** Returns {@code true} if the mode has the Extra-HalfBrite bit set. */
    public static boolean isEhb(int mode)   { return (mode & EHB_BIT)   != 0; }

    // -------------------------------------------------------------------------
    // Pixel aspect derivation
    // -------------------------------------------------------------------------

    /**
     * Derives the {@code BMHD} pixel aspect ratio ({@code xAspect:yAspect}) from a
     * {@code CAMG} viewport mode value.
     *
     * <p>The BMHD aspect fields describe the shape of a single pixel (width:height):
     * <ul>
     *   <li>Hires non-interlaced ({@link #HIRES}): pixels are half as wide as lores
     *       pixels, so {@code xAspect=1, yAspect=2}.</li>
     *   <li>All other combinations (lores, lores+lace, hires+lace):
     *       {@code xAspect=1, yAspect=1}.</li>
     * </ul>
     *
     * <p>Upper bits of the mode word (ECS/AGA display ID) are ignored.
     *
     * @param  mode CAMG viewport mode value
     * @return {@code int[]{xAspect, yAspect}}
     */
    public static int[] aspectFromMode(int mode) {
        return (isHires(mode) && !isLace(mode))
                ? new int[]{1, 2}
                : new int[]{1, 1};
    }

    /**
     * Derives the {@code BMHD} pixel aspect ratio from image dimensions when no
     * {@code CAMG} chunk is available.
     *
     * <p>Mapping:
     * <ul>
     *   <li>640×200 or 640×256 — {@code 1:2} (hires non-interlaced)</li>
     *   <li>320×200, 320×256, 640×400, 640×512, and all other sizes — {@code 1:1}</li>
     * </ul>
     *
     * @param  width  image width in pixels
     * @param  height image height in pixels
     * @return {@code int[]{xAspect, yAspect}}
     */
    public static int[] aspectFromDimensions(int width, int height) {
        if (width == 640 && (height == 200 || height == 256)) {
            return new int[]{1, 2};
        }
        return new int[]{1, 1};
    }
}
