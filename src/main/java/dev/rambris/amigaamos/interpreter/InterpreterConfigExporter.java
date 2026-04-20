/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Map;

/**
 * Exports an {@link InterpreterConfig} to a human-readable JSON file.
 *
 * <p>All named ADAT fields are written as JSON key-value pairs.
 * The {@code defPalette} is a JSON array of 32 integers (12-bit Amiga colour words).
 * The {@code strings} section is a JSON array of objects; each entry carries a
 * {@code "value"} field and, when the slot has a known purpose, a {@code "name"} field.
 * Reserved / runtime fields ({@code piParaTrap}, {@code piAdMouse}, and the two
 * reserved byte ranges) are omitted; the importer always writes 0 for them.
 *
 * <h2>String slot names (1-based AMOS slot → camelCase name)</h2>
 * <pre>
 *  1   systemPath        — base system directory (e.g. "APSystem/")
 *  2   mouseBank         — custom mouse pointer sprite bank (.AmSp); empty = Workbench system pointer
 *  3   fontFile          — custom font loaded at startup; empty = system default
 *  4   defaultIcon       — default icon file
 *  5   autoExec          — auto-execute program
 *  6   editor            — editor program
 *  7   editorConfig      — editor config file
 *  8   defaultResource   — default resource bank
 *  9   systemEquates     — system equates include file path
 *  10  monitor           — monitor program
 *  11  monitorResource   — monitor resource bank
 *  12  helpFile          — help viewer program
 *  13  latestNews        — latest-news file
 *  14  baseLib           — base AMOS library (.Lib)
 *  16–42  ext1…ext27     — extension library slots (.Lib files)
 *  43  printerPort       — printer port device (e.g. "Par:")
 *  44  serialPort        — serial port device  (e.g. "Aux:")
 *  46  cursorFlash       — cursor-flash colour sequence
 *  47  negDirFilter      — negative directory filter pattern
 * </pre>
 */
public class InterpreterConfigExporter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Slot names keyed by 0-based string index.
     * Slots 2, 3, 15, and 45 have no confirmed purpose and are absent from this map.
     */
    private static final Map<Integer, String> SLOT_NAMES;

    static {
        var m = new java.util.LinkedHashMap<Integer, String>();
        m.put(0, "systemPath");
        m.put(1, "mouseBank");    // Mouse.Abk — custom mouse pointer sprite bank (empty = system pointer)
        m.put(2, "fontFile");     // custom font file loaded at startup (empty = system default)
        m.put(3, "defaultIcon");
        m.put(4, "autoExec");
        m.put(5, "editor");
        m.put(6, "editorConfig");
        m.put(7, "defaultResource");
        m.put(8, "systemEquates");
        m.put(9, "monitor");
        m.put(10, "monitorResource");
        m.put(11, "helpFile");
        m.put(12, "latestNews");
        m.put(13, "baseLib");
        // slot 14 — unknown (within system-files range)
        for (int i = 0; i < 27; i++)          // slots 16–42 (0-based 15–41)
            m.put(15 + i, "ext" + (i + 1));
        m.put(42, "printerPort");
        m.put(43, "serialPort");
        // slot 44 — unknown
        m.put(45, "cursorFlash");
        m.put(46, "negDirFilter");
        SLOT_NAMES = Map.copyOf(m);
    }

    public void export(InterpreterConfig config, Path jsonPath) throws IOException {
        var root = JSON.createObjectNode();

        // ── bobs / sprite / copper ────────────────────────────────────────────
        root.put("bobs", config.bobs());
        root.put("defScreenPos", config.defScreenPos());
        root.put("copperListSize", config.copperListSize());
        root.put("spriteLines", config.spriteLines());

        // ── buffer sizes ──────────────────────────────────────────────────────
        root.put("varNameBufSize", config.varNameBufSize());
        root.put("directModeVars", config.directModeVars());
        root.put("defaultBufSize", config.defaultBufSize());

        // ── directory ─────────────────────────────────────────────────────────
        root.put("dirSize", config.dirSize());
        root.put("dirMax", config.dirMax());

        // ── boolean flags ─────────────────────────────────────────────────────
        root.put("printReturn", config.printReturn());
        root.put("icons", config.icons());
        root.put("autoCloseWB", config.autoCloseWB());
        root.put("allowCloseWB", config.allowCloseWB());
        root.put("closeEditor", config.closeEditor());
        root.put("killEditor", config.killEditor());
        root.put("sortFiles", config.sortFiles());
        root.put("showFileSizes", config.showFileSizes());
        root.put("storeDirs", config.storeDirs());

        // ── text reader ───────────────────────────────────────────────────────
        root.put("rtScreenX", config.rtScreenX());
        root.put("rtScreenY", config.rtScreenY());
        root.put("rtWindowX", config.rtWindowX());
        root.put("rtWindowY", config.rtWindowY());
        root.put("rtSpeed", config.rtSpeed());

        // ── file selector ─────────────────────────────────────────────────────
        root.put("fsScrX", config.fsScrX());
        root.put("fsScrY", config.fsScrY());
        root.put("fsWinX", config.fsWinX());
        root.put("fsWinY", config.fsWinY());
        root.put("fsAppSpeed", config.fsAppSpeed());

        // ── default screen ────────────────────────────────────────────────────
        root.put("defScreenWidth", config.defScreenWidth());
        root.put("defScreenHeight", config.defScreenHeight());
        root.put("defScreenPlanes", config.defScreenPlanes());
        root.put("defScreenColors", config.defScreenColors());
        root.put("defScreenMode", config.defScreenMode());
        root.put("defScreenBg", config.defScreenBg());

        var paletteArr = root.putArray("defPalette");
        for (int c : config.defPalette())
            paletteArr.add(c);

        root.put("defWindowX", config.defWindowX());
        root.put("defWindowY", config.defWindowY());
        root.put("amigaAKey", config.amigaAKey());

        // ── strings ───────────────────────────────────────────────────────────
        var stringsArr = root.putArray("strings");
        var strings = config.strings();
        for (int i = 0; i < strings.size(); i++) {
            var entry = stringsArr.addObject();
            String name = SLOT_NAMES.get(i);
            if (name != null) entry.put("name", name);
            entry.put("value", strings.get(i));
        }

        JSON.writeValue(jsonPath.toFile(), root);
    }
}
