/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Exports a {@link MenuBank} to an output directory.
 *
 * <p>Produces a single {@code bank.json} containing the full menu tree.
 * Object blobs are decoded to AMOS {@code Menu$()} embedded-command strings by
 * {@link MenuObjectDecoder}. Flag bits and ink fields are decoded to named JSON properties.
 *
 * <h3>Flag bits (MnFlag high byte)</h3>
 * <pre>
 *   bit 0  Flat      – auto-set on first sibling; never stored in JSON
 *   bit 1  Fixed     – auto-derived from x/y presence; never stored in JSON
 *   bit 2  Sep       – "separate: true"
 *   bit 3  Bar       – style "bar"  (default for depth &gt; 0)
 *   bit 4  Off       – "inactive: true"
 *   bit 5  TLine     – style "tline" (default for depth 0)
 *   bit 6  TMovable  – omitted when true (default); "static: true" when false
 *   bit 7  IMovable  – "itemMovable: true" when set
 * </pre>
 *
 * <h3>Ink fields</h3>
 * <pre>
 *   pen / penSel          – MnInkA1 / MnInkA2 (default pen colour, normal / selected)
 *   paper / paperSel      – MnInkB1 / MnInkB2 (default paper colour)
 *   outline / outlineSel  – MnInkC1 / MnInkC2 (default outline colour)
 * </pre>
 * All ink fields are omitted when zero. Note these are the <em>default</em> colours used before
 * any {@code (INk)} embedded command executes; the embedded command overrides them at render time.
 *
 * <h3>Object fields</h3>
 * <pre>
 *   font            – MnObF  (font object; rarely used)
 *   normal          – MnOb1  (normal / unselected display)
 *   selected        – MnOb2  (highlighted display)
 *   inactiveDisplay – MnOb3  (greyed-out display; rarely used)
 * </pre>
 * Each is an AMOS {@code Menu$()} string (plain text plus optional {@code (XX...)} embedded
 * commands), or absent if the corresponding pointer was zero. {@code itemNumber} and the
 * runtime fields (textX/Y, maxX/Y, xx/yy, zone, adSave, datas, lData) are not stored in JSON.
 *
 * @see MenuBankImporter
 */
public class MenuBankExporter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    // Flag-bit constants (operate on the HIGH byte of the MnFlag word)
    private static final int FL_FLAT     = 1 << 8;  // bit 0 of high byte → word bit 8
    private static final int FL_FIXED    = 1 << 9;
    private static final int FL_SEP      = 1 << 10;
    private static final int FL_BAR      = 1 << 11;
    private static final int FL_OFF      = 1 << 12;
    private static final int FL_TOTAL    = 1 << 13;
    private static final int FL_TBOUGE   = 1 << 14;
    private static final int FL_BOUGE    = 1 << 15;

    public void export(MenuBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        var root = JSON.createObjectNode();
        root.put("type", "Menu");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());
        root.set("items", buildArray(bank.items(), 0));

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s (%d top-level items)%n", dest, bank.items().size());
    }

    private ArrayNode buildArray(List<MenuNode> items, int depth) {
        var arr = JSON.createArrayNode();
        for (var node : items) {
            arr.add(buildItem(node, depth));
        }
        return arr;
    }

    private ObjectNode buildItem(MenuNode node, int depth) {
        var obj = JSON.createObjectNode();
        int flags = node.flags();

        // ── style (omit when it matches the depth-appropriate default) ──
        boolean isBar   = (flags & FL_BAR)   != 0;
        boolean isTotal = (flags & FL_TOTAL)  != 0;
        var style = isBar ? "bar" : isTotal ? "tline" : "line";
        var defaultStyle = (depth == 0) ? "tline" : "bar";
        if (!style.equals(defaultStyle)) obj.put("style", style);

        // ── behaviour flags (omit when default/false) ──
        if ((flags & FL_SEP)    != 0) obj.put("separate",    true);
        if ((flags & FL_OFF)    != 0) obj.put("inactive",    true);
        if ((flags & FL_TBOUGE) == 0) obj.put("static",      true);  // default is movable
        if ((flags & FL_BOUGE)  != 0) obj.put("itemMovable", true);

        // ── position (omit when zero) ──
        putNonZero(obj, "x", node.x());
        putNonZero(obj, "y", node.y());

        // ── keyboard shortcut (omit when unused) ──
        putNonZero(obj, "keyFlag",     node.keyFlag());
        putNonZero(obj, "keyAscii",    node.keyAscii());
        putNonZero(obj, "keyScancode", node.keyScancode());
        putNonZero(obj, "keyShift",    node.keyShift());

        // ── display objects ──
        putDecoded(obj, "font",            node.fontObject());
        putDecoded(obj, "normal",          node.normalObject());
        putDecoded(obj, "selected",        node.selectedObject());
        putDecoded(obj, "inactiveDisplay", node.inactiveObject());

        // ── default ink colours (pen/paper/outline for normal and selected state) ──
        putNonZero(obj, "pen",           node.inkA1());
        putNonZero(obj, "paper",         node.inkB1());
        putNonZero(obj, "outline",       node.inkC1());
        putNonZero(obj, "penSel",        node.inkA2());
        putNonZero(obj, "paperSel",      node.inkB2());
        putNonZero(obj, "outlineSel",    node.inkC2());

        // ── children ──
        var children = buildArray(node.children(), depth + 1);
        if (!children.isEmpty()) obj.set("items", children);

        return obj;
    }

    /** Decodes a blob to a string and adds it to the object; omits the key if null. */
    private static void putDecoded(ObjectNode obj, String key, byte[] blob) {
        var decoded = MenuObjectDecoder.decode(blob);
        if (decoded != null) obj.put(key, decoded);
    }

    /** Adds an integer field only when it is non-zero. */
    private static void putNonZero(ObjectNode obj, String key, int value) {
        if (value != 0) obj.put(key, value);
    }
}
