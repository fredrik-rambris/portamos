/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;

/**
 * Generates a JSON definition skeleton from an AMOS Professional extension binary (.Lib file).
 *
 * <p>The Amiga hunk-format binary contains a token-table with each entry's name, parameter
 * signature string, and library-routine offsets.  This generator extracts every token group
 * (a keyword plus all its argument-count variants) and writes a JSON file with:
 * <ul>
 *   <li>one definition per keyword</li>
 *   <li>one signature per binary form, each with {@code offset} and {@code commaGroups}</li>
 *   <li>skeleton parameter objects derived from the binary param string (types correct,
 *       names are placeholders — fill in from the manual)</li>
 * </ul>
 *
 * <p>Binary parameter string format (first char = token type):
 * <pre>
 *   I   instruction
 *   0   function returning integer
 *   1   function returning float
 *   2   function returning string
 *   V   reserved variable
 *
 *   Remaining chars:
 *   0/1/2/3   value parameter (integer / float / string / amal-string)
 *   t         "To" keyword separator
 *   ,         comma separator between parameters (display only)
 * </pre>
 *
 * Usage (from Main):
 *   portamos --gen-ext-json &lt;input.Lib&gt; --slot &lt;n&gt; [--start &lt;s&gt;] &lt;output.json&gt;
 */
public class ExtJsonGenerator {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads {@code binPath}, extracts all token groups, and writes a JSON skeleton to {@code outPath}.
     */
    public static void generate(Path binPath, int slot, int start, Path outPath) throws IOException {
        var data = Files.readAllBytes(binPath);
        var groups = parseExtension(data, start);

        var root = JSON.createObjectNode();

        var ext = root.putObject("extension");
        ext.put("id",       binPath.getFileName().toString().replaceFirst("(?i)\\.lib$", ""));
        ext.put("name",     "");
        ext.put("filename", binPath.getFileName().toString());
        ext.put("slot",     slot);
        ext.put("start",    start);
        ext.put("vendor",   "");

        var defs = root.putArray("definitions");
        var seen = new HashSet<String>();
        int sigTotal = 0;

        for (var group : groups) {
            var normName = group.name().strip().toUpperCase();
            if (!seen.add(normName)) continue;

            var def = defs.addObject();
            def.put("name", normName);
            def.put("kind", inferKind(group));
            def.put("documentation", "");

            var sigsNode = def.putArray("signatures");
            for (var sd : group.signatures()) {
                var sig = sigsNode.addObject();
                sig.put("offset", sd.offset());
                sig.put("commaGroups", cgFromParams(sd.params()));
                sig.put("presentation", buildPresentation(normName, sd.params()));
                sig.set("parameters", buildParameters(sd.params()));
            }
            sigTotal += group.signatures().size();
        }

        JSON.writeValue(outPath.toFile(), root);
        System.out.printf("Written %d definitions (%d signatures) to %s%n",
                defs.size(), sigTotal, outPath);
    }

    // -------------------------------------------------------------------------
    // Internal data model
    // -------------------------------------------------------------------------

    record SignatureData(int offset, String params) {}
    record GroupedEntry(String name, List<SignatureData> signatures) {}

    // -------------------------------------------------------------------------
    // Binary parsing
    // -------------------------------------------------------------------------

    /**
     * Parses the token table and groups multi-form entries (those linked by the
     * {@code -2} / {@code $80} convention) into a single {@link GroupedEntry}.
     *
     * <p>Each entry in the token table is:
     * <pre>
     *   [4 bytes]  routine offsets (skipped)
     *   [n bytes]  name: ASCII bytes, last byte has bit 7 set; or 0x80 alone = "same name"
     *   [m bytes]  param string: ASCII bytes all < 0xFD
     *   [1 byte]   terminator: 0xFF = last form, 0xFE = more forms follow for same keyword
     *   [0–1 byte] padding to even address
     * </pre>
     */
    static List<GroupedEntry> parseExtension(byte[] src, int start) {
        var groups = new ArrayList<GroupedEntry>();
        if (src.length < 54) return groups;
        if (leek(src, 0) != 0x3F3L) { System.err.println("Bad hunk header"); return groups; }
        if (leek(src, 24) != 0x3E9L) { System.err.println("Bad code hunk"); return groups; }

        int tkoff = (int) leek(src, 32) + 32 + 18;
        if (leek(src, 32 + 18) == 0x41503230L) tkoff += 4;

        // Current open group (name + accumulated signatures)
        String currentName = null;
        var currentSigs = new ArrayList<SignatureData>();

        int pos = tkoff + start;
        while (pos + 4 <= src.length) {
            int offset = (pos - tkoff) & 0xFFFF;
            if (deek(src, pos) == 0) break;
            pos += 4;

            // Parse name bytes (last byte has bit 7 set)
            int nameStart = pos;
            while (pos < src.length && (src[pos] & 0x80) == 0) pos++;
            if (pos >= src.length) break;
            pos++;
            var nameBytes = Arrays.copyOfRange(src, nameStart, pos);

            // Parse param bytes (all < 0xFD)
            int paramStart = pos;
            while (pos < src.length && (src[pos] & 0xFF) < 0xFD) pos++;
            if (pos >= src.length) break;
            int terminator = src[pos] & 0xFF;
            var params = new String(Arrays.copyOfRange(src, paramStart, pos),
                    StandardCharsets.ISO_8859_1);
            pos++;
            if ((pos & 1) != 0) pos++;

            var name = decodeName(nameBytes);
            if (name != null && name.startsWith("!")) {
                name = name.substring(1).strip();
            }

            if (name != null) {
                // New named entry: close previous group and start a new one
                if (currentName != null && !currentSigs.isEmpty()) {
                    groups.add(new GroupedEntry(currentName, List.copyOf(currentSigs)));
                    currentSigs.clear();
                }
                currentName = name.strip();
            }
            // else: $80 continuation — keep currentName, append to currentSigs

            if (currentName == null || currentName.isBlank()) continue;
            currentSigs.add(new SignatureData(offset, params));

            if (terminator == 0xFF) {
                // Last form of this keyword
                groups.add(new GroupedEntry(currentName, List.copyOf(currentSigs)));
                currentSigs.clear();
                currentName = null;
            }
        }

        // Flush any unclosed group
        if (currentName != null && !currentSigs.isEmpty()) {
            groups.add(new GroupedEntry(currentName, List.copyOf(currentSigs)));
        }

        return groups;
    }

    // -------------------------------------------------------------------------
    // Parameter string helpers
    // -------------------------------------------------------------------------

    /**
     * Computes commaGroups from a binary parameter string.
     *
     * <p>commaGroups = count(value params) − count('t' separators)
     * This matches what {@code AsciiParser.countCommaGroups()} returns for the
     * corresponding source text.
     */
    static int cgFromParams(String params) {
        int values = 0, keywords = 0;
        for (int i = 1; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '0' || c == '1' || c == '2' || c == '3') values++;
            else if (c == 't') keywords++;
        }
        return Math.max(values - keywords, 0);
    }

    /**
     * Builds the parameters array for a signature from the binary param string.
     * Parameter names are placeholders ("param1", "param2", …); fill in from the manual.
     */
    private static ArrayNode buildParameters(String params) {
        var arr = JSON.createArrayNode();
        if (params.isEmpty()) return arr;
        int idx = 1;
        for (int i = 1; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '0' || c == '1' || c == '2' || c == '3') {
                var p = arr.addObject();
                p.put("kind", "value");
                p.put("name", "param" + idx++);
                p.put("valueType", c == '1' ? "float" : c == '2' || c == '3' ? "string" : "integer");
            } else if (c == 't') {
                var p = arr.addObject();
                p.put("kind", "keyword");
                p.put("keyword", "To");
            }
            // ',' is a visual separator in the param string, skip
        }
        return arr;
    }

    /**
     * Builds a placeholder presentation string from the keyword name and binary param string.
     * Example: "SAM PLAY" + "I0,0,0" → "Sam Play param1, param2, param3"
     */
    private static String buildPresentation(String name, String params) {
        var title = toTitleCase(name);
        if (params.length() <= 1) return title;

        char type = params.charAt(0);
        boolean isFunc = (type == '0' || type == '1' || type == '2');

        var args = new StringBuilder();
        int idx = 1;
        for (int i = 1; i < params.length(); i++) {
            char c = params.charAt(i);
            if (c == '0' || c == '1' || c == '2' || c == '3') {
                // Append ", " unless we're right after a "To "
                if (!args.isEmpty() && args.charAt(args.length() - 1) != ' ') {
                    args.append(", ");
                }
                args.append("param").append(idx++);
            } else if (c == 't') {
                if (!args.isEmpty()) args.append(' ');
                args.append("To ");
            }
        }

        var argStr = args.toString().stripTrailing();
        if (argStr.isEmpty()) return title;
        return isFunc ? title + "(" + argStr + ")" : title + " " + argStr;
    }

    // -------------------------------------------------------------------------
    // Kind inference
    // -------------------------------------------------------------------------

    private static String inferKind(GroupedEntry group) {
        if (group.signatures().isEmpty()) return "instruction";
        var params = group.signatures().get(0).params();
        if (params.isEmpty()) return "instruction";
        return switch (params.charAt(0)) {
            case '0', '1', '2' -> "function";
            case 'V'           -> "variable";
            default            -> "instruction";
        };
    }

    // -------------------------------------------------------------------------
    // Name decoding
    // -------------------------------------------------------------------------

    private static String decodeName(byte[] raw) {
        if (raw.length == 0) return null;
        int first = raw[0] & 0xFF;
        if (first == 0x80) return null;
        var sb = new StringBuilder();
        boolean capNext = true;
        for (byte b : raw) {
            char c = (char) (b & 0x7F);
            if (capNext && c >= 'a' && c <= 'z') c = (char) (c - ('a' - 'A'));
            capNext = (c == ' ');
            sb.append(c);
        }
        var s = sb.toString();
        return s.endsWith(" ") ? s.substring(0, s.length() - 1) : s;
    }

    private static String toTitleCase(String upper) {
        var sb = new StringBuilder();
        boolean cap = true;
        for (char c : upper.toCharArray()) {
            if (c == ' ') { sb.append(c); cap = true; }
            else if (cap) { sb.append(c); cap = false; }
            else sb.append(Character.toLowerCase(c));
        }
        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Low-level helpers
    // -------------------------------------------------------------------------

    private static long leek(byte[] d, int o) {
        return ((d[o] & 0xFFL) << 24) | ((d[o+1] & 0xFFL) << 16)
             | ((d[o+2] & 0xFFL) <<  8) | (d[o+3] & 0xFFL);
    }

    private static int deek(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o+1] & 0xFF);
    }
}
