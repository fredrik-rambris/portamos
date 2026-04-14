/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports a {@link MenuBank} from a JSON metadata file previously produced by
 * {@link MenuBankExporter}.
 *
 * <p>Reconstructs the {@code MnFlag} word from named JSON properties:
 * <ul>
 *   <li>{@code style} – "bar" (default depth&gt;0) | "tline" (default depth=0) | "line"</li>
 *   <li>{@code separate} – boolean, default false (Menu Separate)</li>
 *   <li>{@code inactive} – boolean, default false (Menu Inactive)</li>
 *   <li>{@code static} – boolean, default false; true = not user-draggable (Menu Static)</li>
 *   <li>{@code itemMovable} – boolean, default false (Menu Item Movable)</li>
 * </ul>
 *
 * <p>Structural flag bits are auto-derived:
 * <ul>
 *   <li>{@code Flat} (bit 0) – set on the first sibling at each level</li>
 *   <li>{@code Fixed} (bit 1) – set when {@code x} or {@code y} is present</li>
 * </ul>
 *
 * <p>Item numbers are generated sequentially (1-based) within each sibling group.
 *
 * <p>Runtime fields (textX/Y, maxX/Y, xx/yy, zone, adSave, datas, lData) are always
 * written as 0; AMOS recalculates them via {@code Menu Calc} at runtime.
 */
public class MenuBankImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    // Flag-bit masks (high byte of the MnFlag word)
    private static final int FL_FLAT     = 1 << 8;
    private static final int FL_FIXED    = 1 << 9;
    private static final int FL_SEP      = 1 << 10;
    private static final int FL_BAR      = 1 << 11;
    private static final int FL_OFF      = 1 << 12;
    private static final int FL_TOTAL    = 1 << 13;
    private static final int FL_TBOUGE   = 1 << 14;
    private static final int FL_BOUGE    = 1 << 15;

    public MenuBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var bankNumber = (short) root.path("bankNumber").asInt(1);
        var chipRam    = root.path("chipRam").asBoolean(false);

        List<MenuNode> items = readItems(root.path("items"), jsonPath.getParent(), 0);

        return new MenuBank(bankNumber, chipRam, List.copyOf(items));
    }

    private static List<MenuNode> readItems(JsonNode arr, Path dir, int depth) throws IOException {
        List<MenuNode> result = new ArrayList<>();
        if (arr == null || arr.isMissingNode()) return result;
        int idx = 0;
        for (var element : arr) {
            result.add(readNode(element, dir, depth, idx == 0, idx + 1));
            idx++;
        }
        return result;
    }

    private static MenuNode readNode(JsonNode n, Path dir, int depth, boolean firstInGroup,
                                     int itemNumber) throws IOException {

        // ── flags ──────────────────────────────────────────────────────────────
        int flags;
        JsonNode rawFlags = n.path("flags");
        if (!rawFlags.isMissingNode() && rawFlags.isNumber()) {
            // Legacy JSON with a raw flags integer — use it directly.
            flags = rawFlags.asInt(0);
        } else {
            flags = reconstructFlags(n, depth, firstInGroup);
        }

        // ── position ────────────────────────────────────────────────────────────
        int x = n.path("x").asInt(0);
        int y = n.path("y").asInt(0);

        // ── keyboard shortcut ───────────────────────────────────────────────────
        int keyFlag     = n.path("keyFlag").asInt(0);
        int keyAscii    = n.path("keyAscii").asInt(0);
        int keyScancode = n.path("keyScancode").asInt(0);
        int keyShift    = n.path("keyShift").asInt(0);

        // ── display objects ─────────────────────────────────────────────────────
        byte[] fontObject     = readBlob(n.path("font"),            dir);
        byte[] normalObject   = readBlob(n.path("normal"),          dir);
        byte[] selectedObject = readBlob(n.path("selected"),        dir);
        byte[] inactiveObject = readBlob(n.path("inactiveDisplay"), dir);

        // ── inks ────────────────────────────────────────────────────────────────
        int inkA1 = n.path("pen").asInt(0);
        int inkB1 = n.path("paper").asInt(0);
        int inkC1 = n.path("outline").asInt(0);
        int inkA2 = n.path("penSel").asInt(0);
        int inkB2 = n.path("paperSel").asInt(0);
        int inkC2 = n.path("outlineSel").asInt(0);

        // ── children ────────────────────────────────────────────────────────────
        // Accept both "items" (new schema) and "children" (legacy schema).
        JsonNode childArr = n.path("items");
        if (childArr.isMissingNode()) childArr = n.path("children");
        List<MenuNode> children = readItems(childArr, dir, depth + 1);

        return new MenuNode(
                itemNumber, flags,
                x, y, 0, 0, 0, 0, 0, 0,
                0,
                keyFlag, keyAscii, keyScancode, keyShift,
                fontObject, normalObject, selectedObject, inactiveObject,
                0, 0, 0,
                inkA1, inkB1, inkC1,
                inkA2, inkB2, inkC2,
                children
        );
    }

    // -------------------------------------------------------------------------
    // Flag reconstruction
    // -------------------------------------------------------------------------

    private static int reconstructFlags(JsonNode n, int depth, boolean firstInGroup) {
        int flags = 0;

        // Structural bits – auto-derived
        if (firstInGroup)                        flags |= FL_FLAT;   // head of lateral chain
        if (n.path("x").asInt(0) != 0
         || n.path("y").asInt(0) != 0)           flags |= FL_FIXED;  // manually positioned

        // Style: "bar" | "line" | "tline"; default depends on depth
        String defaultStyle = (depth == 0) ? "tline" : "bar";
        String style = n.path("style").asText(defaultStyle);
        switch (style) {
            case "bar"   -> flags |= FL_BAR;
            case "tline" -> flags |= FL_TOTAL;
            // "line" → neither bar nor total
        }

        // Behaviour bits
        if (n.path("separate").asBoolean(false))   flags |= FL_SEP;
        if (n.path("inactive").asBoolean(false))   flags |= FL_OFF;
        if (!n.path("static").asBoolean(false))    flags |= FL_TBOUGE; // movable by default
        if (n.path("itemMovable").asBoolean(false)) flags |= FL_BOUGE;

        return flags;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a blob from a string field: if the value ends with {@code .bin} it is read as a
     * raw file; otherwise it is treated as an embedded-command string and encoded by
     * {@link MenuObjectEncoder}.
     */
    private static byte[] readBlob(JsonNode node, Path dir) throws IOException {
        if (node == null || node.isNull() || node.isMissingNode()) return null;
        String value = node.asText(null);
        if (value == null || value.isEmpty()) return null;

        if (value.endsWith(".bin")) {
            Path blobPath = dir.resolve(value);
            if (!Files.exists(blobPath)) {
                throw new IOException("Object blob not found: " + blobPath);
            }
            byte[] data = Files.readAllBytes(blobPath);
            if (data.length < 2) {
                throw new IOException("Object blob too short: " + blobPath);
            }
            return data;
        }

        return MenuObjectEncoder.encode(value);
    }
}
