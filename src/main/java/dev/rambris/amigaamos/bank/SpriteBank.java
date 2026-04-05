package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * In-memory model of an AMOS Professional Sprite (or Icon) bank.
 *
 * <p>Sprite banks use the {@code AmSp} on-disk format, which differs completely from the
 * regular {@code AmBk} format: there is no bank number or chip/fast RAM flag in the file.
 * Both types are always chip RAM; sprite banks map to bank 1, icon banks to bank 2.
 *
 * <p>On-disk layout ({@code AmSp} / {@code AmIc}):
 * <pre>
 *   [4]   magic "AmSp" (sprites) or "AmIc" (icons)
 *   [2]   number of sprites/icons (N)
 *   For each of the N sprites:
 *     [2]  widthWords  — width in 16-bit words (pixel width = widthWords × 16)
 *     [2]  height      — height in pixels
 *     [2]  planes      — number of bitplanes (1–6)
 *     [2]  hotspotX    — hot-spot X coordinate
 *     [2]  hotspotY    — hot-spot Y coordinate
 *     [widthWords×2×height×planes]  Amiga planar bitmap data
 *   (empty sprites have widthWords=height=planes=0 and no data bytes)
 *   [64]  colour palette — 32 × uint16 Amiga $0RGB colour words
 * </pre>
 */
public record SpriteBank(
        Type bankType,          // SPRITES or ICONS
        List<Sprite> sprites,
        int[] palette           // 32 entries, 12-bit Amiga $0RGB colour
) implements AmosBank {

    /**
     * A single sprite or bob image.
     *
     * <p>Bitmap data is stored in Amiga planar format: all rows of plane 0, then all rows of
     * plane 1, … Each row is {@code widthWords} big-endian 16-bit words wide.
     *
     * @param widthWords width in 16-bit words (pixel width = widthWords × 16)
     * @param height     height in pixels
     * @param planes     number of bitplanes (1–6; 0 for empty sprites)
     * @param hotspotX   hot-spot X coordinate
     * @param hotspotY   hot-spot Y coordinate
     * @param data       raw Amiga planar bitmap; length = widthWords × 2 × height × planes bytes
     */
    public record Sprite(
            int widthWords,
            int height,
            int planes,
            int hotspotX,
            int hotspotY,
            byte[] data
    ) {
        /** Returns the pixel width of this sprite (widthWords × 16). */
        public int widthPixels() {
            return widthWords * 16;
        }

        /** Returns {@code true} if this is an empty/placeholder sprite (all dimensions zero). */
        public boolean isEmpty() {
            return widthWords == 0 && height == 0 && planes == 0;
        }
    }

    /** The 4-byte on-disk magic corresponding to this bank's type. */
    public String magic() {
        return bankType == Type.ICONS ? "AmIc" : "AmSp";
    }

    @Override
    public Type type() {
        return bankType;
    }

    /** Sprite banks are bank 1; icon banks are bank 2. */
    @Override
    public short bankNumber() {
        return (short) (bankType == Type.ICONS ? 2 : 1);
    }

    /** Both sprite and icon banks always reside in chip RAM. */
    @Override
    public boolean chipRam() {
        return true;
    }

    @Override
    public BankWriter writer() {
        return new SpriteBankWriter();
    }
}
