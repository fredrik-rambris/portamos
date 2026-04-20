/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.interpreter;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AMOSPro Interpreter Config file into an {@link InterpreterConfig}.
 *
 * <p>See {@link InterpreterConfig} for the binary layout.
 */
public class InterpreterConfigReader {

    private static final int MAX_STRINGS = 64;

    public static InterpreterConfig read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static InterpreterConfig read(byte[] raw) throws IOException {
        if (raw.length < 8)
            throw new IOException("File too short to be an Interpreter Config (" + raw.length + " bytes)");

        var magic = new String(raw, 0, 4, StandardCharsets.US_ASCII);
        if (!"PId1".equals(magic))
            throw new IOException("Not an AMOSPro Interpreter Config: magic=\"" + magic + "\"");

        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);
        buf.position(4);
        int adatLength = buf.getInt();

        if (raw.length < 8 + adatLength + 8)
            throw new IOException("File truncated before PIt1 section");

        // ── ADAT block ────────────────────────────────────────────────────────
        int piParaTrap = buf.getInt();          // @0
        int piAdMouse = buf.getInt();          // @4
        int bobs = buf.getShort() & 0xFFFF; // @8
        int defScreenPos = buf.getShort() & 0xFFFF; // @10
        int copperListSize = buf.getInt();          // @12
        int spriteLines = buf.getInt();          // @16
        int varNameBufSize = buf.getInt();          // @20
        int directModeVars = buf.getShort() & 0xFFFF; // @24
        int defaultBufSize = buf.getInt();          // @26
        int dirSize = buf.getShort() & 0xFFFF; // @30
        int dirMax = buf.getShort() & 0xFFFF; // @32

        boolean printReturn = buf.get() != 0;      // @34
        boolean icons = buf.get() != 0;      // @35
        boolean autoCloseWB = buf.get() != 0;      // @36
        boolean allowCloseWB = buf.get() != 0;      // @37
        boolean closeEditor = buf.get() != 0;      // @38
        boolean killEditor = buf.get() != 0;      // @39
        boolean sortFiles = buf.get() != 0;      // @40
        boolean showFileSizes = buf.get() != 0;      // @41
        boolean storeDirs = buf.get() != 0;      // @42
        buf.position(buf.position() + 5);           // @43-47: reserved

        int rtScreenX = buf.getShort() & 0xFFFF; // @48
        int rtScreenY = buf.getShort() & 0xFFFF; // @50
        int rtWindowX = buf.getShort() & 0xFFFF; // @52
        int rtWindowY = buf.getShort() & 0xFFFF; // @54
        int rtSpeed = buf.getShort() & 0xFFFF; // @56

        int fsScrX = buf.getShort() & 0xFFFF; // @58
        int fsScrY = buf.getShort() & 0xFFFF; // @60
        int fsWinX = buf.getShort() & 0xFFFF; // @62
        int fsWinY = buf.getShort() & 0xFFFF; // @64
        int fsAppSpeed = buf.getShort() & 0xFFFF; // @66

        int defScreenWidth = buf.getShort() & 0xFFFF; // @68
        int defScreenHeight = buf.getShort() & 0xFFFF; // @70
        int defScreenPlanes = buf.getShort() & 0xFFFF; // @72
        int defScreenColors = buf.getShort() & 0xFFFF; // @74
        int defScreenMode = buf.getShort() & 0xFFFF; // @76
        int defScreenBg = buf.getShort() & 0xFFFF; // @78

        var palette = new ArrayList<Integer>(32);   // @80
        for (int i = 0; i < 32; i++)
            palette.add(buf.getShort() & 0xFFFF);

        int defWindowX = buf.getShort() & 0xFFFF;  // @144
        int defWindowY = buf.getShort() & 0xFFFF;  // @146
        int amigaAKey = buf.getInt();              // @148
        buf.position(buf.position() + 24);           // @152-175: reserved (ds.l 6)

        // ── PIt1 string table ─────────────────────────────────────────────────
        var pit1Magic = new String(raw, 8 + adatLength, 4, StandardCharsets.US_ASCII);
        if (!"PIt1".equals(pit1Magic))
            throw new IOException("Expected PIt1 at offset " + (8 + adatLength) + ", found \"" + pit1Magic + "\"");

        int a = 8 + adatLength + 8; // skip "PIt1" + length field
        var strings = new ArrayList<String>();

        for (int st = 0; st < MAX_STRINGS; st++) {
            if (a + 2 > raw.length)
                throw new IOException("Unexpected end of file inside string table");
            int length = raw[a + 1] & 0xFF;
            if (length == 0xFF) break;
            if (a + 2 + length > raw.length)
                throw new IOException("String entry extends beyond end of file at offset " + a);
            strings.add(new String(raw, a + 2, length, StandardCharsets.ISO_8859_1));
            a += length + 2;
        }

        return new InterpreterConfig(
                piParaTrap, piAdMouse,
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
