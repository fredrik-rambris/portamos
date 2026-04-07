package dev.rambris.iff;

import java.nio.charset.StandardCharsets;

/** Well-known IFF chunk and form type identifiers for the ILBM image format. */
public enum IlbmId implements IffId {

    FORM(),
    ILBM(),
    BMHD(),
    CMAP(),
    CAMG(),
    BODY();

    private final byte[] id;

    IlbmId(String s) {
        this.id = s.getBytes(StandardCharsets.US_ASCII);
    }
    IlbmId() {
        this.id = name().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public byte[] id() {
        return id;
    }
}
