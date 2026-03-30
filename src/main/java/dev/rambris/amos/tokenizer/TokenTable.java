package dev.rambris.amos.tokenizer;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Loads and indexes the AMOS token tables from the Amiga hunk-format extension binaries.
 *
 * Key format: (slot << 16) | offset
 *   - slot 0 = core (00base.bin), loaded with start=-194 to include operator tokens
 *   - slot 1+ = extension libraries, loaded with start=6
 *
 * For encoding:
 *   - slot 0 token: write key & 0xFFFF as uint16 directly
 *   - slot N token: write 0x004E + slot byte + 0x00 + (key & 0xFFFF) as uint16
 */
class TokenTable {

    /** Maps normalized uppercase keyword name → encoding key. */
    private final Map<String, Integer> nameToKey = new HashMap<>();

    TokenTable() {
        loadResource("/amos/extensions/core.bin", 0, -194);
        loadResource("/amos/extensions/music.bin", 1, 6);
        loadResource("/amos/extensions/compact.bin", 2, 6);
        loadResource("/amos/extensions/request.bin", 3, 6);
        loadResource("/amos/extensions/ioports.bin", 6, 6);
    }

    /** Returns the encoding key for the given keyword, or null if not found. */
    Integer lookup(String name) {
        return nameToKey.get(name.toUpperCase());
    }

    private void loadResource(String path, int slot, int start) {
        try (InputStream is = TokenTable.class.getResourceAsStream(path)) {
            if (is == null) return;
            byte[] data = is.readAllBytes();
            parseExtension(data, slot, start);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load token table from " + path, e);
        }
    }

    private void parseExtension(byte[] src, int slot, int start) {
        if (src.length < 54) return;
        // Verify Amiga hunk header
        if (leek(src, 0) != 0x3F3L) return;  // HUNK_HEADER
        if (leek(src, 24) != 0x3E9L) return; // HUNK_CODE

        // Compute token table base offset
        int tkoff = (int) leek(src, 32) + 32 + 18;
        if (leek(src, 32 + 18) == 0x41503230L) tkoff += 4; // AP20 tag

        String lastName = null;
        int pos = tkoff + start;

        while (pos + 4 <= src.length) {
            // Compute encoding key: slot in upper 16 bits, offset from tkoff in lower 16
            int offset = (pos - tkoff) & 0xFFFF;
            int key = (slot << 16) | offset;

            // End marker: instruction pointer = 0
            if (deek(src, pos) == 0) break;
            pos += 4; // skip instruction + function pointers

            // Read name bytes; last byte has its high bit set as terminator
            int nameStart = pos;
            while (pos < src.length && (src[pos] & 0x80) == 0) pos++;
            if (pos >= src.length) break;
            pos++; // include the high-bit terminator byte
            byte[] nameBytes = Arrays.copyOfRange(src, nameStart, pos);

            // Read type parameter (terminated by 0xFD, 0xFE, or 0xFF)
            while (pos < src.length && (src[pos] & 0xFF) < 0xFD) pos++;
            if (pos >= src.length) break;
            pos++; // skip type terminator

            // Word-align position
            if ((pos & 1) != 0) pos++;

            // Decode the display name
            String name = decodeName(nameBytes);
            if (name == null) {
                name = lastName; // 0x80 prefix: reuse previous name
            } else if (name.startsWith("!")) {
                lastName = name.substring(1); // save for subsequent reuse
                name = lastName;
            }

            if (name == null || name.isBlank()) continue;

            // Normalize: strip leading/trailing spaces, uppercase
            String normalized = name.strip().toUpperCase();
            if (!normalized.isEmpty()) {
                nameToKey.putIfAbsent(normalized, key);
            }
        }
    }

    /**
     * Decodes a raw name byte array from the binary token table into a display string.
     *
     * The Amiga hunk binary stores names as ASCII bytes with the high bit set on
     * the last character. Spaces within the name cause the next letter to be capitalized.
     * A leading 0x80 byte means "use the previous entry's name" (returns null).
     */
    private static String decodeName(byte[] raw) {
        if (raw.length == 0) return null;
        int first = raw[0] & 0xFF;
        if (first == 0x80) return null; // sentinel: reuse lastName

        StringBuilder sb = new StringBuilder();
        boolean capitalizeNext = true;
        for (byte b : raw) {
            char c = (char) (b & 0x7F);
            if (capitalizeNext && c >= 'a' && c <= 'z') {
                c = (char) (c - ('a' - 'A'));
            }
            capitalizeNext = (c == ' ');
            sb.append(c);
        }

        // Remove trailing space (marks this token as a multi-word keyword prefix)
        String s = sb.toString();
        if (s.endsWith(" ")) {
            s = s.substring(0, s.length() - 1);
        }
        return s;
    }

    // -------------------------------------------------------------------------
    // Binary read helpers (big-endian)
    // -------------------------------------------------------------------------

    private static long leek(byte[] d, int o) {
        return ((d[o] & 0xFFL) << 24) | ((d[o + 1] & 0xFFL) << 16)
                | ((d[o + 2] & 0xFFL) << 8) | (d[o + 3] & 0xFFL);
    }

    private static int deek(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }
}
