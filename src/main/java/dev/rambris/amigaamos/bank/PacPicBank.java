package dev.rambris.amigaamos.bank;

/**
 * In-memory model of an AMOS {@code Pac.Pic.} bank stored in an {@code AmBk} container.
 *
 * <p>A Pac.Pic bank payload can be either:
 * <ul>
 *   <li><b>PACK</b>: one packed bitmap starting with magic {@code 0x06071963}</li>
 *   <li><b>SPACK</b>: a screen header starting with {@code 0x12031990}, followed by one packed bitmap</li>
 * </ul>
 */
public record PacPicBank(
        short bankNumber,
        boolean chipRam,
        ScreenHeader screenHeader, // null for plain PACK banks
        byte[] picData             // starts with 0x06071963
) implements AmosBank {

    /**
     * Extra screen metadata present only for SPACK banks.
     *
     * <p>On-disk layout (90 bytes total):
     * <pre>
     * [4]  0x12031990 (PS_MAGIC)
     * [2]  width
     * [2]  height
     * [2]  hardX
     * [2]  hardY
     * [2]  displayWidth
     * [2]  displayHeight
     * [2]  offsetX
     * [2]  offsetY
     * [2]  bplCon0
     * [2]  numColors
     * [2]  numPlanes
     * [64] palette (32 x uint16, Amiga 0x0RGB)
     * </pre>
     */
    public record ScreenHeader(
            int width,
            int height,
            int hardX,
            int hardY,
            int displayWidth,
            int displayHeight,
            int offsetX,
            int offsetY,
            int bplCon0,
            int numColors,
            int numPlanes,
            int[] palette
    ) {}

    public boolean isSpack() {
        return screenHeader != null;
    }

    @Override
    public Type type() {
        return Type.PACPIC;
    }

    @Override
    public BankWriter writer() {
        return new PacPicBankWriter();
    }
}

