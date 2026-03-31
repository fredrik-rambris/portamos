package dev.rambris.amos.tokenizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Generates a JSON definition skeleton from an AMOS Professional extension binary (.Lib file).
 *
 * The Amiga hunk-format binary contains the token name table.  This generator extracts every
 * token name and its byte offset and writes them as a JSON file suitable for use as an
 * amos/definitions/*.json resource.  The resulting file will need manual enrichment for
 * documentation, parameter signatures, and links — but it provides the offset values required
 * for tokenization.
 *
 * Usage (from Main):
 *   portamos --gen-ext-json <input.Lib> --slot <n> [--start <s>] <output.json>
 *
 * <p>Default {@code start} is 6 for extension slots (≥1) and -194 for the core (slot 0).</p>
 */
public class ExtJsonGenerator {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Reads {@code binPath}, extracts all token entries, and writes a JSON skeleton to {@code outPath}.
     *
     * @param binPath path to the .Lib / .bin file
     * @param slot    extension slot number (0 = core)
     * @param start   byte offset from the token-table base to the first entry
     *                (typically -194 for core, 6 for extensions)
     * @param outPath destination .json file
     */
    public static void generate(Path binPath, int slot, int start, Path outPath) throws IOException {
        byte[] data = Files.readAllBytes(binPath);
        var entries = parseExtension(data, start);

        ObjectNode root = JSON.createObjectNode();

        ObjectNode ext = root.putObject("extension");
        ext.put("id",       binPath.getFileName().toString().replaceFirst("(?i)\\.lib$", ""));
        ext.put("name",     "");          // fill in manually
        ext.put("filename", binPath.getFileName().toString());
        ext.put("slot",     slot);
        ext.put("start",    start);
        ext.put("vendor",   "");          // fill in manually

        ArrayNode defs = root.putArray("definitions");
        Set<String> seen = new HashSet<>();
        for (var e : entries) {
            String normName = e.name().strip().toUpperCase();
            if (!seen.add(normName)) continue; // first occurrence only

            ObjectNode def = defs.addObject();
            def.put("name",   normName);
            def.put("kind",   inferKind(normName));
            def.put("offset", e.offset());
            def.put("documentation", "");  // fill in manually
            def.putArray("signatures");    // fill in manually
        }

        JSON.writeValue(outPath.toFile(), root);
        System.out.println("Written " + defs.size() + " definitions to " + outPath);
    }

    // -------------------------------------------------------------------------
    // Internal parsing — mirrors TokenTable.parseExtension
    // -------------------------------------------------------------------------

    record Entry(String name, int offset) {}

    private static java.util.List<Entry> parseExtension(byte[] src, int start) {
        var results = new java.util.ArrayList<Entry>();
        if (src.length < 54) return results;
        if (leek(src, 0) != 0x3F3L) { System.err.println("Bad hunk header"); return results; }
        if (leek(src, 24) != 0x3E9L) { System.err.println("Bad code hunk"); return results; }

        int tkoff = (int) leek(src, 32) + 32 + 18;
        if (leek(src, 32 + 18) == 0x41503230L) tkoff += 4; // AP20 tag

        String lastName = null;
        int pos = tkoff + start;
        Set<String> seen = new HashSet<>();

        while (pos + 4 <= src.length) {
            int offset = (pos - tkoff) & 0xFFFF;
            if (deek(src, pos) == 0) break;
            pos += 4;

            int nameStart = pos;
            while (pos < src.length && (src[pos] & 0x80) == 0) pos++;
            if (pos >= src.length) break;
            pos++;
            byte[] nameBytes = Arrays.copyOfRange(src, nameStart, pos);

            while (pos < src.length && (src[pos] & 0xFF) < 0xFD) pos++;
            if (pos >= src.length) break;
            pos++;
            if ((pos & 1) != 0) pos++;

            String name = decodeName(nameBytes);
            if (name == null) {
                name = lastName;
            } else if (name.startsWith("!")) {
                lastName = name.substring(1);
                name = lastName;
            }

            if (name == null || name.isBlank()) continue;
            String normalized = name.strip().toUpperCase();
            if (!normalized.isEmpty() && seen.add(normalized)) {
                results.add(new Entry(normalized, offset));
            }
        }
        return results;
    }

    /** Heuristic: names ending in $ or returning values are likely functions. */
    private static String inferKind(String name) {
        if (name.endsWith("$") || name.endsWith("#")) return "function";
        return "instruction"; // most tokens are instructions; correct manually
    }

    private static String decodeName(byte[] raw) {
        if (raw.length == 0) return null;
        int first = raw[0] & 0xFF;
        if (first == 0x80) return null;
        StringBuilder sb = new StringBuilder();
        boolean capNext = true;
        for (byte b : raw) {
            char c = (char) (b & 0x7F);
            if (capNext && c >= 'a' && c <= 'z') c = (char) (c - ('a' - 'A'));
            capNext = (c == ' ');
            sb.append(c);
        }
        String s = sb.toString();
        return s.endsWith(" ") ? s.substring(0, s.length() - 1) : s;
    }

    private static long leek(byte[] d, int o) {
        return ((d[o] & 0xFFL) << 24) | ((d[o+1] & 0xFFL) << 16)
             | ((d[o+2] & 0xFFL) <<  8) | (d[o+3] & 0xFFL);
    }

    private static int deek(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o+1] & 0xFF);
    }
}
