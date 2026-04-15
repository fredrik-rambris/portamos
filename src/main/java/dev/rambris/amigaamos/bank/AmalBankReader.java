/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AMOS Professional AMAL bank (.Abk) file into an {@link AmalBank}.
 *
 * <p>Binary layout (big-endian, after the AmBk envelope):
 * <pre>
 *   --- Payload ---
 *   [4]   Strings-Start   byte offset from payload[0] to the Programs section
 *   --- Moves section (at payload[4] = "Moves") ---
 *   [2]   Number_Of_Movements
 *   [2×n] X-offsets: (XMove_NN − Moves) / 2   (word offset; 0 = undefined)
 *   [2×n] Y-offsets: (YMove_NN − Moves) / 2   (word offset; 0 = undefined)
 *   [8×n] 8-byte ASCII name per movement
 *   --- Movement data ---
 *   XMove_NN:
 *     [2]   Speed  (1/50 sec intervals between steps)
 *     [2]   Length (bytes of encoded movement data following)
 *     [n]   Encoded data (see below)
 *   YMove_NN:
 *     [n]   Encoded data only (no Speed/Length header); terminated by 0x00
 *   --- Programs section (at payload[Strings-Start] = "Progs") ---
 *   [2]   Number_Of_Programs
 *   [2×n] Word offsets: (Prog_NN − Progs) / 2
 *   Prog_NN:
 *     [2]   Length of program in bytes (including this length word)
 *     [n]   ASCII text; '~' is the line separator
 * </pre>
 *
 * <p>Movement byte encoding:
 * <ul>
 *   <li>{@code 0x00} — end-of-move sentinel</li>
 *   <li>{@code 0x01..0x7F} — delta: 7-bit signed pixels (0x40..0x7F = negative: value − 128)</li>
 *   <li>{@code 0x80..0xFF} — wait: {@code byte & 0x7F} intervals</li>
 * </ul>
 */
public class AmalBankReader {

    public static AmalBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static AmalBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        if (hdr.type() != AmosBank.Type.AMAL) {
            throw new IOException("Expected \"" + AmosBank.Type.AMAL.identifier()
                    + "\" bank, got: \"" + hdr.typeName() + "\"");
        }

        var payload = hdr.payload();
        var buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);

        // ---- Strings-Start pointer ----
        int stringsStart = buf.getInt();   // byte offset from payload[0] to Progs section

        // ---- Moves section starts at payload[4] ----
        // movesBase is the byte offset within payload where "Moves" label sits.
        // All XMove/YMove word-offsets are relative to this position.
        final int movesBase = 4;
        buf.position(movesBase);
        int numMovements = buf.getShort() & 0xFFFF;

        var xOffsets = new int[numMovements];
        var yOffsets = new int[numMovements];
        for (int i = 0; i < numMovements; i++) xOffsets[i] = buf.getShort() & 0xFFFF;
        for (int i = 0; i < numMovements; i++) yOffsets[i] = buf.getShort() & 0xFFFF;

        var names = new String[numMovements];
        for (int i = 0; i < numMovements; i++) {
            var nameBuf = new byte[8];
            buf.get(nameBuf);
            names[i] = new String(nameBuf, StandardCharsets.ISO_8859_1).stripTrailing();
        }

        // ---- Decode movement data ----
        var movements = new ArrayList<AmalBank.Movement>(numMovements);
        for (int i = 0; i < numMovements; i++) {
            AmalBank.MovementData xMove = null;
            AmalBank.MovementData yMove = null;

            if (xOffsets[i] != 0) {
                int xBytePos = movesBase + xOffsets[i] * 2;
                if (xBytePos + 4 <= stringsStart) {
                    buf.position(xBytePos);
                    int speed = buf.getShort() & 0xFFFF;
                    int length = buf.getShort() & 0xFFFF;
                    var data = new byte[Math.min(length, payload.length - buf.position())];
                    buf.get(data);
                    xMove = new AmalBank.MovementData(speed, decodeMovement(data));
                }
            }

            if (yOffsets[i] != 0) {
                int yBytePos = movesBase + yOffsets[i] * 2;
                // Y movement has no Speed/Length header; validate it falls within movement area
                if (yBytePos < stringsStart) {
                    xMove = xMove != null ? xMove : new AmalBank.MovementData(1, List.of());
                    buf.position(yBytePos);
                    yMove = new AmalBank.MovementData(xMove.speed(), decodeMovementUntilEnd(buf, stringsStart));
                }
            }

            movements.add(new AmalBank.Movement(names[i], xMove, yMove));
        }

        // ---- Parse programs section ----
        List<String> programs = List.of();
        if (stringsStart > 0 && stringsStart < payload.length) {
            buf.position(stringsStart);
            programs = parsePrograms(buf, stringsStart);
        }

        return new AmalBank(hdr.bankNumber(), hdr.chipRam(), List.copyOf(movements), programs);
    }

    // -------------------------------------------------------------------------
    // Movement decoding
    // -------------------------------------------------------------------------

    private static List<AmalBank.Instruction> decodeMovement(byte[] data) {
        var instructions = new ArrayList<AmalBank.Instruction>();
        int start = 0;
        // The movement table begins with a 0x00 sentinel (for backwards playback); skip it.
        if (data.length > 0 && (data[0] & 0xFF) == 0x00) start = 1;
        for (int i = start; i < data.length; i++) {
            int v = data[i] & 0xFF;
            if (v == 0x00) break;                          // end sentinel
            if ((v & 0x80) != 0) {                         // WAIT
                instructions.add(new AmalBank.Instruction.Wait(v & 0x7F));
            } else {                                        // DELTA (7-bit signed)
                int delta = (v & 0x40) != 0 ? v - 128 : v;
                instructions.add(new AmalBank.Instruction.Delta(delta));
            }
        }
        return instructions;
    }

    private static List<AmalBank.Instruction> decodeMovementUntilEnd(ByteBuffer buf, int limit) {
        var instructions = new ArrayList<AmalBank.Instruction>();
        while (buf.position() < limit && buf.hasRemaining()) {
            int v = buf.get() & 0xFF;
            if (v == 0x00) break;
            if ((v & 0x80) != 0) {
                instructions.add(new AmalBank.Instruction.Wait(v & 0x7F));
            } else {
                int delta = (v & 0x40) != 0 ? v - 128 : v;
                instructions.add(new AmalBank.Instruction.Delta(delta));
            }
        }
        return instructions;
    }

    // -------------------------------------------------------------------------
    // Programs section
    // -------------------------------------------------------------------------

    private static List<String> parsePrograms(ByteBuffer buf, int progsBase) {
        int numPrograms = buf.getShort() & 0xFFFF;
        var wordOffsets = new int[numPrograms];
        for (int i = 0; i < numPrograms; i++) {
            wordOffsets[i] = buf.getShort() & 0xFFFF;
        }

        var programs = new ArrayList<String>(numPrograms);
        for (int i = 0; i < numPrograms; i++) {
            // Offsets are word offsets from the byte AFTER the count word (progsBase+2).
            // An offset of 0 is the null/empty sentinel.
            if (wordOffsets[i] == 0) {
                programs.add("");
                continue;
            }
            int progPos = progsBase + 2 + wordOffsets[i] * 2;
            if (progPos + 2 > buf.capacity()) {
                programs.add("");
                continue;
            }
            buf.position(progPos);
            // Length word = number of bytes following it (not including the length word itself).
            int lengthWord = buf.getShort() & 0xFFFF;
            int textLen = lengthWord;
            if (textLen <= 0 || progPos + 2 + textLen > buf.capacity()) {
                programs.add("");
                continue;
            }
            var textBytes = new byte[textLen];
            buf.get(textBytes);
            // Strip trailing nulls
            int strLen = textLen;
            while (strLen > 0 && textBytes[strLen - 1] == 0) strLen--;
            programs.add(new String(textBytes, 0, strLen, StandardCharsets.ISO_8859_1));
        }
        return programs;
    }
}
