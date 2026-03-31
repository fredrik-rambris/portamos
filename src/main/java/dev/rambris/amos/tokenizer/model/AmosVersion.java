package dev.rambris.amos.tokenizer.model;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public enum AmosVersion {
    PRO_101("AMOS Pro101v"),
    BASIC_134("AMOS Basic V134 "),
    BASIC_13("AMOS Basic v1.3 ");

    private final String versionString;

    AmosVersion(String versionString) {
        this.versionString = versionString;
    }

    /**
     * Returns the 16-byte header field. For Pro versions the trailing 4 bytes are zeros.
     * For AMOS Basic 1.3 it's already exactly 16 bytes (with trailing space).
     */
    public byte[] headerBytes() {
        byte[] header = new byte[16];
        byte[] strBytes = versionString.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(strBytes, 0, header, 0, Math.min(strBytes.length, 16));
        return header;
    }

    public String headerString() {
        return versionString;
    }
}
