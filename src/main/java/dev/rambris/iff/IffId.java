package dev.rambris.iff;

import java.nio.charset.StandardCharsets;

/**
 * A four-character IFF chunk or form type identifier.
 *
 * <p>Implemented by enums (e.g. {@link IlbmId}, {@link Svx8Id}) for known ID sets,
 * and by {@link FourCC} for ad-hoc identifiers.
 */
public interface IffId {

    /** The four raw ASCII bytes of this identifier. */
    byte[] id();

    /** The identifier as a four-character ASCII string. */
    default String asString() {
        return new String(id(), StandardCharsets.US_ASCII);
    }

    IffId FORM = FourCC.of("FORM");
}
