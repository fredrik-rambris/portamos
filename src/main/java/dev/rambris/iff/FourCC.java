package dev.rambris.iff;

import java.nio.charset.StandardCharsets;

/**
 * A concrete {@link IffId} for arbitrary four-character IFF identifiers.
 *
 * <p>Use the enum types ({@link IlbmId}, {@link Svx8Id}) for well-known IDs;
 * use {@code FourCC.of("XXXX")} for one-off or unknown identifiers.
 */
public final class FourCC implements IffId {

    private final byte[] id;

    private FourCC(byte[] id) {
        this.id = id;
    }

    /**
     * Creates a {@code FourCC} from a four-character ASCII string.
     *
     * @throws IllegalArgumentException if {@code s} is not exactly 4 characters
     */
    public static FourCC of(String s) {
        if (s == null || s.length() != 4) {
            throw new IllegalArgumentException("FourCC must be exactly 4 characters: " + s);
        }
        return new FourCC(s.getBytes(StandardCharsets.US_ASCII));
    }

    @Override
    public byte[] id() {
        return id;
    }

    @Override
    public String toString() {
        return asString();
    }
}
