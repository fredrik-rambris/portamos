/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.interpreter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class InterpreterConfigTest {

    private static final Path REFERENCE = Path.of("src/test/resources/AMOSPro_Interpreter_Config");

    // ── ADAT field parsing ────────────────────────────────────────────────────

    @Test
    void readsRuntimeFields() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(0, c.piParaTrap(), "piParaTrap");
        assertEquals(0, c.piAdMouse(), "piAdMouse");
    }

    @Test
    void readsBobsAndSprite() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(68, c.bobs(), "bobs");
        assertEquals(50, c.defScreenPos(), "defScreenPos");
        assertEquals(12288, c.copperListSize(), "copperListSize");
        assertEquals(128, c.spriteLines(), "spriteLines");
    }

    @Test
    void readsBufferSizes() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(4096, c.varNameBufSize(), "varNameBufSize");
        assertEquals(252, c.directModeVars(), "directModeVars");
        assertEquals(32768, c.defaultBufSize(), "defaultBufSize");
    }

    @Test
    void readsDirectorySettings() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(30, c.dirSize(), "dirSize");
        assertEquals(128, c.dirMax(), "dirMax");
    }

    @Test
    void readsFlags() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertTrue(c.printReturn(), "printReturn");
        assertFalse(c.icons(), "icons");
        assertFalse(c.autoCloseWB(), "autoCloseWB");
        assertTrue(c.allowCloseWB(), "allowCloseWB");
        assertTrue(c.sortFiles(), "sortFiles");
        assertTrue(c.showFileSizes(), "showFileSizes");
        assertTrue(c.storeDirs(), "storeDirs");
    }

    @Test
    void readsDefaultScreen() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(320, c.defScreenWidth(), "defScreenWidth");
        assertEquals(200, c.defScreenHeight(), "defScreenHeight");
        assertEquals(4, c.defScreenPlanes(), "defScreenPlanes");
        assertEquals(16, c.defScreenColors(), "defScreenColors");
        assertEquals(0, c.defScreenMode(), "defScreenMode");
        assertEquals(0, c.defScreenBg(), "defScreenBg");
    }

    @Test
    void readsDefaultPalette() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        var p = c.defPalette();
        assertEquals(32, p.size(), "palette size");
        assertEquals(0x000, (int) p.get(0), "colour 0");
        assertEquals(0xA40, (int) p.get(1), "colour 1");
        assertEquals(0xFFF, (int) p.get(2), "colour 2");
        assertEquals(0x000, (int) p.get(3), "colour 3");
        assertEquals(0xF00, (int) p.get(4), "colour 4");
        assertEquals(0x0F0, (int) p.get(5), "colour 5");
        assertEquals(0x00F, (int) p.get(6), "colour 6");
        assertEquals(0x666, (int) p.get(7), "colour 7");
        assertEquals(0x555, (int) p.get(8), "colour 8");
        assertEquals(0x333, (int) p.get(9), "colour 9");
        // colours 16-31 should be 0
        for (int i = 16; i < 32; i++)
            assertEquals(0, (int) p.get(i), "colour " + i);
    }

    @Test
    void readsAmigaAKey() throws Exception {
        var c = InterpreterConfigReader.read(REFERENCE);
        assertEquals(0x00404161, c.amigaAKey(), "amigaAKey");
    }

    // ── string table ──────────────────────────────────────────────────────────

    @Test
    void readsStrings() throws Exception {
        var strings = InterpreterConfigReader.read(REFERENCE).strings();
        assertEquals("APSystem/", strings.get(0));
        assertEquals("", strings.get(1));
        assertEquals("", strings.get(2));
        assertEquals("Def_Icon", strings.get(3));
        assertEquals("AutoExec.AMOS", strings.get(4));
        assertEquals("AMOSPro_Editor", strings.get(5));
        assertEquals("AMOSPro_Editor_Config", strings.get(6));
        assertEquals("AMOSPro_Default_Resource.Abk", strings.get(7));
        assertEquals("AMOSPro_Productivity1:Equates/AMOSPro_System_Equates", strings.get(8));
        assertEquals("AMOSPro_Monitor", strings.get(9));
        assertEquals("AMOSPro_Monitor_Resource.Abk", strings.get(10));
        assertEquals("AMOSPro_Accessories:AMOSPro_Help/AMOSPro_Help", strings.get(11));
        assertEquals("AMOSPro_Accessories:AMOSPro_Help/LatestNews", strings.get(12));
        assertEquals("AMOSPro.Lib", strings.get(13));
        assertEquals("", strings.get(14));
        assertEquals("AMOSPro_Music.Lib", strings.get(15));
        assertEquals("AMOSPro_Compact.Lib", strings.get(16));
        assertEquals("AMOSPro_Request.Lib", strings.get(17));
        assertEquals("", strings.get(18));
        assertEquals("AMOSPro_Compiler.Lib", strings.get(19));
        assertEquals("AMOSPro_IOPorts.Lib", strings.get(20));
        // slot 42 (0-based): Par:
        assertEquals("Par:", strings.get(42));
        // slot 43: Aux:
        assertEquals("Aux:", strings.get(43));
        // slot 45 (0-based): cursor flash string
        assertEquals("(000,2)(440,2)(880,2)(bb0,2)(dd0,2)(ee0,2)(ff2,2)(ff8,2)(ffc,2)(fff,2)(aaf,2)(88c,2)(66a,2)(226,2)(004,2)(001,2)",
                strings.get(45));
    }

    // ── round-trips ───────────────────────────────────────────────────────────

    @Test
    void writeRoundTrip(@TempDir Path tmp) throws Exception {
        var original = InterpreterConfigReader.read(REFERENCE);
        Path written = tmp.resolve("AMOSPro_Interpreter_Config");
        new InterpreterConfigWriter().write(original, written);

        var readback = InterpreterConfigReader.read(written);
        assertEquals(original.bobs(), readback.bobs());
        assertEquals(original.copperListSize(), readback.copperListSize());
        assertEquals(original.defPalette(), readback.defPalette());
        assertEquals(original.amigaAKey(), readback.amigaAKey());
        assertEquals(original.strings(), readback.strings());
    }

    @Test
    void importExportRoundTrip(@TempDir Path tmp) throws Exception {
        var original = InterpreterConfigReader.read(REFERENCE);
        Path jsonPath = tmp.resolve("config.json");

        new InterpreterConfigExporter().export(original, jsonPath);
        assertTrue(jsonPath.toFile().exists(), "JSON file must exist");

        var imported = new InterpreterConfigImporter().importFrom(jsonPath);
        assertEquals(original.bobs(), imported.bobs());
        assertEquals(original.copperListSize(), imported.copperListSize());
        assertEquals(original.defPalette(), imported.defPalette());
        assertEquals(original.amigaAKey(), imported.amigaAKey());
        assertEquals(original.strings(), imported.strings());
    }

    @Test
    void exportedJsonShape(@TempDir Path tmp) throws Exception {
        var config = InterpreterConfigReader.read(REFERENCE);
        Path jsonPath = tmp.resolve("config.json");
        new InterpreterConfigExporter().export(config, jsonPath);

        var json = new ObjectMapper().readTree(jsonPath.toFile());
        assertTrue(json.has("bobs"), "JSON must have bobs");
        assertTrue(json.has("defPalette"), "JSON must have defPalette");
        assertTrue(json.get("defPalette").isArray(), "defPalette must be array");
        assertEquals(32, json.get("defPalette").size(), "palette must have 32 entries");
        assertTrue(json.has("strings"), "JSON must have strings");
        assertTrue(json.get("strings").isArray(), "strings must be array");
        assertFalse(json.has("piParaTrap"), "runtime field must not appear in JSON");
        assertFalse(json.has("piAdMouse"), "runtime field must not appear in JSON");
    }
}
