package dev.rambris.amigaamos.bank;

/**
 * Shared constants for AMOS Pac.Pic and SPACK payloads.
 */
public final class PacPicFormat {

    private PacPicFormat() {
    }

    // PACK (Pac.Pic.)
    public static final int PK_MAGIC = 0x06071963;
    public static final int PACK_HEADER_SIZE = 24;

    // SPACK screen wrapper header (before PK_MAGIC payload)
    public static final int SPACK_MAGIC = 0x12031990;
    public static final int SPACK_HEADER_SIZE = 90;

    // Offsets inside PACK header
    public static final int OFF_PKDX = 4;    // source X in bytes
    public static final int OFF_PKDY = 6;    // source Y in pixels
    public static final int OFF_PKTX = 8;    // width in bytes
    public static final int OFF_PKTY = 10;   // number of lumps
    public static final int OFF_PKTCAR = 12; // lines per lump
    public static final int OFF_PKPLAN = 14; // bitplanes
    public static final int OFF_RLEOFF = 16;
    public static final int OFF_PTSOFF = 20;
}

