/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.interpreter;

import java.util.List;

/**
 * In-memory representation of an AMOSPro Interpreter Config file
 * ({@code AMOSPro_Interpreter_Config}).
 *
 * <p>Binary layout (from {@code +Interpreter_Config.s}):
 * <pre>
 *   [4]   "PId1"   — file magic
 *   [4]   uint32   — length of ADAT block (always 0xB0 = 176 bytes)
 *   [176] ADAT     — interpreter settings (see field documentation below)
 *   [4]   "PIt1"   — string-table magic
 *   [4]   uint32   — length of string table
 *   [n]   entries  — repeated [0x00][length][ISO-8859-1 bytes]; [0x00][0xFF] terminates
 * </pre>
 *
 * <h2>ADAT field offsets (relative to start of ADAT block)</h2>
 * <pre>
 *   @0 piParaTrap      dc.l  — runtime trap address (0 in file)
 *   @4 piAdMouse       dc.l  — runtime mouse address (0 in file)
 *   @8 bobs            dc.w  — number of bobs (default 68)
 *   @10 defScreenPos    dc.w  — default screen position (default 50)
 *   @12 copperListSize  dc.l  — copper list size (default 12288)
 *   @16 spriteLines     dc.l  — number of sprite lines (default 128)
 *   @20 varNameBufSize  dc.l  — variable name buffer size (default 4096)
 *   @24 directModeVars  dc.w  — direct-mode variable slots (default 252)
 *   @26 defaultBufSize  dc.l  — default buffer size (default 32768)
 *   @30 dirSize         dc.w  — directory size (default 30)
 *   @32 dirMax          dc.w  — directory max (default 128)
 *   @34 printReturn     dc.b  — print carriage-return flag
 *   @35 icons           dc.b  — icons enabled
 *   @36 autoCloseWB     dc.b  — auto-close Workbench
 *   @37 allowCloseWB    dc.b  — close-Workbench effective
 *   @38 closeEditor     dc.b  — close-editor effective
 *   @39 killEditor      dc.b  — kill-editor effective
 *   @40 sortFiles       dc.b  — sort files in requester
 *   @41 showFileSizes   dc.b  — show file sizes in requester
 *   @42 storeDirs       dc.b  — store directory paths
 *   @43 (reserved 5 bytes)
 *   @48 rtScreenX       dc.w  — text-reader screen width  (default 640)
 *   @50 rtScreenY       dc.w  — text-reader screen height (default 200)
 *   @52 rtWindowX       dc.w  — text-reader window X      (default 129)
 *   @54 rtWindowY       dc.w  — text-reader window Y      (default 50)
 *   @56 rtSpeed         dc.w  — text-reader appear speed  (default 8)
 *   @58 fsScrX          dc.w  — file-selector screen X    (default 448)
 *   @60 fsScrY          dc.w  — file-selector screen Y    (default 158)
 *   @62 fsWinX          dc.w  — file-selector window X    (default 177)
 *   @64 fsWinY          dc.w  — file-selector window Y    (default 70)
 *   @66 fsAppSpeed      dc.w  — file-selector appear speed(default 8)
 *   @68 defScreenWidth  dc.w  — default screen width      (default 320)
 *   @70 defScreenHeight dc.w  — default screen height     (default 200)
 *   @72 defScreenPlanes dc.w  — default bit-planes        (default 4)
 *   @74 defScreenColors dc.w  — default colour count      (default 16)
 *   @76 defScreenMode   dc.w  — default screen mode       (default 0)
 *   @78 defScreenBg     dc.w  — default background colour (default 0)
 *   @80 defPalette[32]  dc.w  — default 32-entry palette (12-bit Amiga colour each)
 *   @144 defWindowX      dc.w  — default window X          (default 0)
 *   @146 defWindowY      dc.w  — default window Y          (default 0)
 *   @148 amigaAKey       dc.l  — AMIGA-A key code          (default 0x00404161)
 *   @152 (reserved 24 bytes — ds.l 6)
 * </pre>
 *
 * <p>Up to 64 path strings follow in the PIt1 section (ST$(1)…ST$(64)).
 * Empty strings are valid; string order is significant.
 */
public record InterpreterConfig(
        // @0  runtime trap address — 0 in file, filled by interpreter at load time
        int piParaTrap,
        // @4  runtime mouse address — 0 in file, filled by interpreter at load time
        int piAdMouse,

        int bobs,              // @8
        int defScreenPos,      // @10
        int copperListSize,    // @12
        int spriteLines,       // @16
        int varNameBufSize,    // @20
        int directModeVars,    // @24
        int defaultBufSize,    // @26
        int dirSize,           // @30
        int dirMax,            // @32

        boolean printReturn,   // @34
        boolean icons,         // @35
        boolean autoCloseWB,   // @36
        boolean allowCloseWB,  // @37
        boolean closeEditor,   // @38
        boolean killEditor,    // @39
        boolean sortFiles,     // @40
        boolean showFileSizes, // @41
        boolean storeDirs,     // @42
        // @43-47: reserved 5 bytes (normally 0, not exposed in JSON)

        int rtScreenX,         // @48
        int rtScreenY,         // @50
        int rtWindowX,         // @52
        int rtWindowY,         // @54
        int rtSpeed,           // @56

        int fsScrX,            // @58
        int fsScrY,            // @60
        int fsWinX,            // @62
        int fsWinY,            // @64
        int fsAppSpeed,        // @66

        int defScreenWidth,    // @68
        int defScreenHeight,   // @70
        int defScreenPlanes,   // @72
        int defScreenColors,   // @74
        int defScreenMode,     // @76
        int defScreenBg,       // @78

        List<Integer> defPalette, // @80  32 × 12-bit Amiga colour words

        int defWindowX,        // @144
        int defWindowY,        // @146
        int amigaAKey,         // @148
        // @152-175: reserved 24 bytes (ds.l 6, normally 0, not exposed in JSON)

        List<String> strings
) {
}
