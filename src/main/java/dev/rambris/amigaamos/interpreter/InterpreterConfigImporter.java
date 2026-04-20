/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports an {@link InterpreterConfig} from a JSON file produced by {@link InterpreterConfigExporter}.
 *
 * <p>Reserved and runtime fields ({@code piParaTrap}, {@code piAdMouse}, and the two
 * reserved byte ranges) are not read from JSON; they are always set to 0.
 */
public class InterpreterConfigImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    public InterpreterConfig importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        int bobs = root.path("bobs").asInt(68);
        int defScreenPos = root.path("defScreenPos").asInt(50);
        int copperListSize = root.path("copperListSize").asInt(12 * 1024);
        int spriteLines = root.path("spriteLines").asInt(128);

        int varNameBufSize = root.path("varNameBufSize").asInt(4 * 1024);
        int directModeVars = root.path("directModeVars").asInt(42 * 6);
        int defaultBufSize = root.path("defaultBufSize").asInt(32 * 1024);

        int dirSize = root.path("dirSize").asInt(30);
        int dirMax = root.path("dirMax").asInt(128);

        boolean printReturn = root.path("printReturn").asBoolean(true);
        boolean icons = root.path("icons").asBoolean(false);
        boolean autoCloseWB = root.path("autoCloseWB").asBoolean(false);
        boolean allowCloseWB = root.path("allowCloseWB").asBoolean(true);
        boolean closeEditor = root.path("closeEditor").asBoolean(true);
        boolean killEditor = root.path("killEditor").asBoolean(true);
        boolean sortFiles = root.path("sortFiles").asBoolean(true);
        boolean showFileSizes = root.path("showFileSizes").asBoolean(true);
        boolean storeDirs = root.path("storeDirs").asBoolean(true);

        int rtScreenX = root.path("rtScreenX").asInt(640);
        int rtScreenY = root.path("rtScreenY").asInt(200);
        int rtWindowX = root.path("rtWindowX").asInt(129);
        int rtWindowY = root.path("rtWindowY").asInt(50);
        int rtSpeed = root.path("rtSpeed").asInt(8);

        int fsScrX = root.path("fsScrX").asInt(448);
        int fsScrY = root.path("fsScrY").asInt(158);
        int fsWinX = root.path("fsWinX").asInt(177);
        int fsWinY = root.path("fsWinY").asInt(70);
        int fsAppSpeed = root.path("fsAppSpeed").asInt(8);

        int defScreenWidth = root.path("defScreenWidth").asInt(320);
        int defScreenHeight = root.path("defScreenHeight").asInt(200);
        int defScreenPlanes = root.path("defScreenPlanes").asInt(4);
        int defScreenColors = root.path("defScreenColors").asInt(16);
        int defScreenMode = root.path("defScreenMode").asInt(0);
        int defScreenBg = root.path("defScreenBg").asInt(0);

        var paletteNode = root.path("defPalette");
        var palette = new ArrayList<Integer>(32);
        for (var node : paletteNode)
            palette.add(node.asInt(0));
        while (palette.size() < 32) palette.add(0);

        int defWindowX = root.path("defWindowX").asInt(0);
        int defWindowY = root.path("defWindowY").asInt(0);
        int amigaAKey = root.path("amigaAKey").asInt(0x00404161);

        var stringsNode = root.path("strings");
        var strings = new ArrayList<String>();
        for (var node : stringsNode) {
            // Each entry is either {"name":"...","value":"..."} or a bare string
            strings.add(node.isObject() ? node.path("value").asText("") : node.asText());
        }

        return new InterpreterConfig(
                0, 0,
                bobs, defScreenPos, copperListSize, spriteLines,
                varNameBufSize, directModeVars, defaultBufSize, dirSize, dirMax,
                printReturn, icons, autoCloseWB, allowCloseWB,
                closeEditor, killEditor, sortFiles, showFileSizes, storeDirs,
                rtScreenX, rtScreenY, rtWindowX, rtWindowY, rtSpeed,
                fsScrX, fsScrY, fsWinX, fsWinY, fsAppSpeed,
                defScreenWidth, defScreenHeight, defScreenPlanes, defScreenColors,
                defScreenMode, defScreenBg,
                List.copyOf(palette),
                defWindowX, defWindowY, amigaAKey,
                List.copyOf(strings));
    }
}
