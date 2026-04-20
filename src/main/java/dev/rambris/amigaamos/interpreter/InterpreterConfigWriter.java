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

/**
 * Serializes an {@link InterpreterConfig} to the AMOSPro Interpreter Config binary format.
 *
 * <p>See {@link InterpreterConfig} for the binary layout.
 */
public class InterpreterConfigWriter {

    private static final int ADAT_SIZE = 176; // always 0xB0

    public void write(InterpreterConfig config, Path dest) throws IOException {
        Files.write(dest, toBytes(config));
    }

    public byte[] toBytes(InterpreterConfig config) {
        byte[] stringTable = buildStringTable(config);
        int total = 4 + 4 + ADAT_SIZE + 4 + 4 + stringTable.length;
        var buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);

        buf.put("PId1".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(ADAT_SIZE);

        // ── ADAT block ────────────────────────────────────────────────────────
        buf.putInt(config.piParaTrap());      // @0
        buf.putInt(config.piAdMouse());       // @4
        buf.putShort((short) config.bobs());          // @8
        buf.putShort((short) config.defScreenPos());  // @10
        buf.putInt(config.copperListSize());  // @12
        buf.putInt(config.spriteLines());     // @16
        buf.putInt(config.varNameBufSize());  // @20
        buf.putShort((short) config.directModeVars()); // @24
        buf.putInt(config.defaultBufSize());  // @26
        buf.putShort((short) config.dirSize());        // @30
        buf.putShort((short) config.dirMax());         // @32

        buf.put(flag(config.printReturn()));  // @34
        buf.put(flag(config.icons()));        // @35
        buf.put(flag(config.autoCloseWB())); // @36
        buf.put(flag(config.allowCloseWB())); // @37
        buf.put(flag(config.closeEditor()));  // @38
        buf.put(flag(config.killEditor()));   // @39
        buf.put(flag(config.sortFiles()));    // @40
        buf.put(flag(config.showFileSizes())); // @41
        buf.put(flag(config.storeDirs()));    // @42
        buf.put(new byte[5]);                 // @43-47: reserved

        buf.putShort((short) config.rtScreenX());   // @48
        buf.putShort((short) config.rtScreenY());   // @50
        buf.putShort((short) config.rtWindowX());   // @52
        buf.putShort((short) config.rtWindowY());   // @54
        buf.putShort((short) config.rtSpeed());     // @56

        buf.putShort((short) config.fsScrX());      // @58
        buf.putShort((short) config.fsScrY());      // @60
        buf.putShort((short) config.fsWinX());      // @62
        buf.putShort((short) config.fsWinY());      // @64
        buf.putShort((short) config.fsAppSpeed());  // @66

        buf.putShort((short) config.defScreenWidth());  // @68
        buf.putShort((short) config.defScreenHeight()); // @70
        buf.putShort((short) config.defScreenPlanes()); // @72
        buf.putShort((short) config.defScreenColors()); // @74
        buf.putShort((short) config.defScreenMode());   // @76
        buf.putShort((short) config.defScreenBg());     // @78

        var palette = config.defPalette();              // @80
        for (int i = 0; i < 32; i++)
            buf.putShort(i < palette.size() ? (short) (int) palette.get(i) : 0);

        buf.putShort((short) config.defWindowX());  // @144
        buf.putShort((short) config.defWindowY());  // @146
        buf.putInt(config.amigaAKey());             // @148
        buf.put(new byte[24]);                      // @152-175: reserved (ds.l 6)

        // ── PIt1 string table ─────────────────────────────────────────────────
        buf.put("PIt1".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(stringTable.length);
        buf.put(stringTable);

        return buf.array();
    }

    private static byte[] buildStringTable(InterpreterConfig config) {
        int size = 2; // terminator [0x00][0xFF]
        for (var s : config.strings())
            size += 2 + s.getBytes(StandardCharsets.ISO_8859_1).length;

        var buf = ByteBuffer.allocate(size);
        for (var s : config.strings()) {
            byte[] bytes = s.getBytes(StandardCharsets.ISO_8859_1);
            buf.put((byte) 0x00);
            buf.put((byte) (bytes.length & 0xFF));
            buf.put(bytes);
        }
        buf.put((byte) 0x00);
        buf.put((byte) 0xFF);
        return buf.array();
    }

    private static byte flag(boolean v) {
        return v ? (byte) 1 : 0;
    }
}
