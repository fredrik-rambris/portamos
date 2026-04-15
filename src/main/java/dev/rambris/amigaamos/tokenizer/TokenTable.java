/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and indexes the AMOS token tables from the JSON definition files.
 *
 * Key format: (slot << 16) | offset
 *   - slot 0 = core, operators start at offset 0xFF3E (start=-194)
 *   - slot 1+ = extension libraries (start=6)
 *
 * For encoding:
 *   - slot 0 token: write key & 0xFFFF as uint16 directly
 *   - slot N token: emit as ExtKeyword(slot, offset)
 *
 * Each JSON definition may have multiple signatures, each with its own offset.
 * The tokenizer selects the appropriate signature (and therefore binary token
 * offset) based on the number of comma-separated argument groups on the line.
 */
class TokenTable {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * One binary form of a keyword.
     * {@code commaGroups = valueParamCount - keywordParamCount} is the expected
     * number of top-level comma groups after the keyword (i.e. commas + 1 if
     * there is any argument, but adjusted for keyword separators like "To").
     */
    record SignatureEntry(int key, int commaGroups) {}

    /**
     * Maps normalized uppercase keyword name → ordered list of SignatureEntries.
     * The list is ordered from fewest to most comma groups so that the selection
     * algorithm ("highest commaGroups ≤ actual") works by iterating in order.
     */
    private final Map<String, List<SignatureEntry>> nameToSignatures = new HashMap<>();

    /** Maps encoding key → extra zero bytes to write after the token (back-patch space). */
    private final Map<Integer, Integer> keyToExtraBytes = new HashMap<>();

    /**
     * Maps encoding key → display name for use by the detokenizer.
     * Operators are stored as-is; keywords are converted to title case.
     * Only the first registered name for a key is kept (earliest definition wins).
     */
    private final Map<Integer, String> keyToDisplayName = new HashMap<>();

    /**
     * Keys belonging to operator/punctuation tokens (from {@link #CORE_OPERATORS}).
     * These are formatted differently by the printer — no leading space, no trailing space.
     */
    private final java.util.Set<Integer> operatorKeys = new java.util.HashSet<>();

    /**
     * Core operator and punctuation tokens. These live at fixed offsets in the core
     * token table (slot 0) and are not present in the JSON definition files.
     * Keys are the raw uint16 offset values used directly for encoding (slot 0 → no prefix).
     */
    private static final Map<String, Integer> CORE_OPERATORS = Map.ofEntries(
            Map.entry("XOR",  0xFF3E),
            Map.entry("<>",   0xFF66),
            Map.entry("><",   0xFF70),
            Map.entry("<=",   0xFF7A),
            Map.entry("=<",   0xFF84),
            Map.entry(">=",   0xFF8E),
            Map.entry("=>",   0xFF98),
            Map.entry("=",    0xFFA2),
            Map.entry("<",    0xFFAC),
            Map.entry(">",    0xFFB6),
            Map.entry("+",    0xFFC0),
            Map.entry("-",    0xFFCA),
            Map.entry("MOD",  0xFFD4),
            Map.entry("*",    0xFFE2),
            Map.entry("/",    0xFFEC),
            Map.entry("^",    0xFFF6),
            Map.entry(":",    0x0054),
            Map.entry(",",    0x005C),
            Map.entry(";",    0x0064),
            Map.entry("#",    0x006C),
            Map.entry("(",    0x0074),
            Map.entry(")",    0x007C),
            Map.entry("[",    0x0084),
            Map.entry("]",    0x008C)
    );

    TokenTable() {
        // Register operators as single-entry signature lists and populate reverse map
        for (var entry : CORE_OPERATORS.entrySet()) {
            int key = entry.getValue();
            nameToSignatures.put(entry.getKey(), List.of(new SignatureEntry(key, 0)));
            keyToDisplayName.put(key, entry.getKey()); // operators keep their name as-is
            operatorKeys.add(key);
        }
        loadResource("/amos/definitions/core.json");
        loadResource("/amos/definitions/music.json");
        loadResource("/amos/definitions/compact.json");
        loadResource("/amos/definitions/request.json");
        loadResource("/amos/definitions/ioports.json");
        loadResource("/amos/definitions/compiler.json");
    }

    /**
     * Returns the primary (first) encoding key for the given keyword, or null if not found.
     * Used for operators and as a fallback; prefer {@link #selectKey} for keyword tokens.
     */
    Integer lookup(String name) {
        var sigs = nameToSignatures.get(name.toUpperCase());
        if (sigs == null || sigs.isEmpty()) return null;
        return sigs.get(0).key();
    }

    /**
     * Selects the best-matching encoding key for the given keyword based on the number
     * of top-level comma groups that follow it on the source line.
     *
     * <p>Selection rule: choose the signature whose {@code commaGroups} is the highest
     * value that is still ≤ {@code actualCommaGroups}.  Falls back to the first
     * (fewest-args) signature if no signature is ≤ actualCommaGroups.</p>
     *
     * @param name              the keyword name (case-insensitive)
     * @param actualCommaGroups commaCount + 1 if any arg follows, else 0
     * @return the encoding key, or null if the keyword is not in the table
     */
    Integer selectKey(String name, int actualCommaGroups) {
        var sigs = nameToSignatures.get(name.toUpperCase());
        if (sigs == null || sigs.isEmpty()) return null;
        if (sigs.size() == 1) return sigs.get(0).key();

        // Walk through signatures (ordered fewest→most commaGroups).
        // Keep track of the best candidate = highest commaGroups ≤ actual.
        var best = sigs.get(0);
        for (var sig : sigs) {
            if (sig.commaGroups() <= actualCommaGroups) {
                best = sig;
            }
        }
        return best.key();
    }

    /** Returns the number of extra zero bytes to emit after the given token key, or 0. */
    int extraBytesFor(int key) {
        return keyToExtraBytes.getOrDefault(key, 0);
    }

    /**
     * Returns the display name for a core keyword token key, or {@code null} if unknown.
     * Operators are returned as-is (e.g. {@code "+"}); keywords are returned in title case
     * (e.g. {@code "For"}, {@code "Screen Open"}).
     */
    String displayNameFor(int key) {
        return keyToDisplayName.get(key);
    }

    /**
     * Returns {@code true} if the given key is an operator or punctuation token
     * (i.e. belongs to {@link #CORE_OPERATORS}).  The printer uses this to suppress
     * the leading space that precedes normal keyword tokens.
     */
    boolean isOperator(int key) {
        return operatorKeys.contains(key);
    }

    private void loadResource(String path) {
        try (InputStream is = TokenTable.class.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Missing resource: " + path);
            var root = JSON.readTree(is);
            parseDefinitions(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load token table from " + path, e);
        }
    }

    /**
     * Loads an additional token definition JSON file from the given path.
     */
    void loadFile(Path path) {
        try (var is = Files.newInputStream(path)) {
            var root = JSON.readTree(is);
            parseDefinitions(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load token table from " + path, e);
        }
    }

    private void parseDefinitions(JsonNode root) {
        var ext  = root.path("extension");
        int slot = ext.path("slot").asInt(0);

        var definitions = root.path("definitions");
        for (var defn : definitions) {
            var name = defn.path("name").asText("").strip().toUpperCase();
            if (name.isEmpty()) continue;

            var extraNode  = defn.path("extraBytes");
            var signatures = defn.path("signatures");
            if (signatures.isMissingNode() || !signatures.isArray()) continue;

            var entries = new ArrayList<SignatureEntry>();
            for (var sig : signatures) {
                var offsetNode = sig.path("offset");
                if (offsetNode.isMissingNode()) continue;

                int offset = offsetNode.asInt();
                int key = (slot << 16) | (offset & 0xFFFF);

                // Use explicit "commaGroups" override if present (set on skeleton sigs),
                // otherwise compute from the parameter list.
                int commaGroups;
                var cgOverride = sig.path("commaGroups");
                if (!cgOverride.isMissingNode()) {
                    commaGroups = cgOverride.asInt();
                } else {
                    int valueParams = 0;
                    int keywordParams = 0;
                    var params = sig.path("parameters");
                    if (params.isArray()) {
                        for (var p : params) {
                            var kind = p.path("kind").asText("");
                            if ("value".equals(kind)) valueParams++;
                            else if ("keyword".equals(kind)) keywordParams++;
                        }
                    }
                    commaGroups = Math.max(valueParams - keywordParams, 0);
                }
                entries.add(new SignatureEntry(key, commaGroups));

                // Register extraBytes for this key
                if (!extraNode.isMissingNode()) {
                    keyToExtraBytes.putIfAbsent(key, extraNode.asInt());
                }
            }

            if (!entries.isEmpty()) {
                // Sort by commaGroups ascending so selectKey walks fewest→most
                entries.sort((a, b) -> Integer.compare(a.commaGroups(), b.commaGroups()));
                nameToSignatures.putIfAbsent(name, entries);

                // Populate reverse map: each key → title-case display name.
                // putIfAbsent so the first (core) definition wins over extension aliases.
                var displayName = toDisplayName(name);
                for (var e : entries) {
                    keyToDisplayName.putIfAbsent(e.key(), displayName);
                }
            }
        }
    }

    /**
     * Converts an uppercase keyword name to a title-case display name.
     * Examples: {@code "FOR"} → {@code "For"}, {@code "SCREEN OPEN"} → {@code "Screen Open"}.
     */
    private static String toDisplayName(String upperName) {
        var words = upperName.split(" ");
        var sb = new StringBuilder();
        for (var word : words) {
            if (!sb.isEmpty()) sb.append(' ');
            if (word.isEmpty()) continue;
            sb.append(Character.toUpperCase(word.charAt(0)));
            if (word.length() > 1) sb.append(word.substring(1).toLowerCase());
        }
        return sb.toString();
    }
}
