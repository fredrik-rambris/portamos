package dev.rambris.amos.tokenizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
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
 */
class TokenTable {

    private static final ObjectMapper JSON = new ObjectMapper();

    /** Maps normalized uppercase keyword name → encoding key. */
    private final Map<String, Integer> nameToKey = new HashMap<>();

    /** Maps encoding key → extra zero bytes to write after the token (back-patch space). */
    private final Map<Integer, Integer> keyToExtraBytes = new HashMap<>();

    /**
     * Maps primary key → alternate key for keywords that have two forms.
     * The alternate form is used when the keyword is followed by '(' or a numeric literal,
     * e.g. "Screen Hide 3" (with screen number) vs "Screen Hide" (current screen),
     * or "Colour(n)" (function returning colour) vs "Colour ink,rgb" (command setting colour).
     */
    private final Map<Integer, Integer> keyToAltKey = new HashMap<>();

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
        nameToKey.putAll(CORE_OPERATORS);
        loadResource("/amos/definitions/core.json");
        loadResource("/amos/definitions/music.json");
        loadResource("/amos/definitions/compact.json");
        loadResource("/amos/definitions/request.json");
        loadResource("/amos/definitions/ioports.json");
    }

    /** Returns the encoding key for the given keyword, or null if not found. */
    Integer lookup(String name) {
        return nameToKey.get(name.toUpperCase());
    }

    /**
     * Returns the alternate encoding key for the given keyword, or the primary key if there is
     * no alternate form.  The alternate form is used when the keyword is followed by '(' or a
     * numeric literal (digit, '$', '%').
     */
    Integer lookupAlt(String name) {
        Integer primary = nameToKey.get(name.toUpperCase());
        if (primary == null) return null;
        return keyToAltKey.getOrDefault(primary, primary);
    }

    /** Returns the number of extra zero bytes to emit after the given token key, or 0. */
    int extraBytesFor(int key) {
        return keyToExtraBytes.getOrDefault(key, 0);
    }

    private void loadResource(String path) {
        try (InputStream is = TokenTable.class.getResourceAsStream(path)) {
            if (is == null) throw new RuntimeException("Missing resource: " + path);
            JsonNode root = JSON.readTree(is);
            parseDefinitions(root);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load token table from " + path, e);
        }
    }

    private void parseDefinitions(JsonNode root) {
        JsonNode ext = root.path("extension");
        int slot  = ext.path("slot").asInt(0);
        // start is not needed at read time — offsets are already stored per-definition

        JsonNode definitions = root.path("definitions");
        for (JsonNode defn : definitions) {
            JsonNode offsetNode = defn.path("offset");
            if (offsetNode.isMissingNode()) continue; // no binary offset — skip

            int offset = offsetNode.asInt();
            int key = (slot << 16) | (offset & 0xFFFF);

            String name = defn.path("name").asText("").strip().toUpperCase();
            if (name.isEmpty()) continue;

            nameToKey.putIfAbsent(name, key);

            JsonNode extraNode = defn.path("extraBytes");
            if (!extraNode.isMissingNode()) {
                keyToExtraBytes.putIfAbsent(key, extraNode.asInt());
            }

            JsonNode altNode = defn.path("altOffset");
            if (!altNode.isMissingNode()) {
                int altOffset = altNode.asInt();
                int altKey = (slot << 16) | (altOffset & 0xFFFF);
                keyToAltKey.putIfAbsent(key, altKey);
                // Also register the alt name→key mapping so extraBytesFor works on alt keys
                nameToKey.putIfAbsent(name + "\u0000alt", altKey);
            }
        }
    }
}
