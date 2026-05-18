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
 *   --- Moves section (at payload[4]) ---
 *   [2]   Number_Of_Movements  (n)
 *   [2×n] Word offsets from movesBase to each movement data block (0 = empty slot)
 *   [2×n] Length in bytes of each movement data block (0 = empty slot)
 *   [8×n] 8-byte ASCII name per movement (space-padded)
 *   --- Movement data blocks ---
 *   [2]   Speed  (1/50 sec intervals per step)
 *   [2]   n_x    (number of X-axis step bytes, including sentinels)
 *   [n_x] X step bytes (starts with 0x00 sentinel; 0x00 marks end of forward playback)
 *   [?]   Y step bytes (follows X; terminated by 0x00; length = block_length − 4 − n_x)
 *   --- Programs section (at payload[Strings-Start]) ---
 *   [2]   Number_Of_Programs
 *   [2×n] Word offsets from progsBase+2: (Prog_NN − progsBase − 2) / 2; 0 = empty slot
 *   [2]   0x0000 null guard
 *   [?]   Environment program: [2] length + [n] ASCII text (~ line separator)
 *   Prog_NN:
 *     [2]   Length of program in bytes (padded to even)
 *     [n]   ASCII text; '~' is the line separator
 * </pre>
 *
 * <p>Movement byte encoding:
 * <ul>
 *   <li>{@code 0x00} — end-of-sequence sentinel</li>
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
        // All word-offsets in the movement table are relative to movesBase.
        final int movesBase = 4;
        buf.position(movesBase);
        int numMovements = buf.getShort() & 0xFFFF;

        // First array: word offsets to each movement data block (from movesBase).
        var offsets = new int[numMovements];
        for (int i = 0; i < numMovements; i++) offsets[i] = buf.getShort() & 0xFFFF;

        // Second array: byte lengths of each movement data block.
        var lengths = new int[numMovements];
        for (int i = 0; i < numMovements; i++) lengths[i] = buf.getShort() & 0xFFFF;

        // Names: 8 bytes each, space-padded ASCII.
        var names = new String[numMovements];
        for (int i = 0; i < numMovements; i++) {
            var nameBuf = new byte[8];
            buf.get(nameBuf);
            names[i] = new String(nameBuf, StandardCharsets.ISO_8859_1).stripTrailing();
        }

        // ---- Decode movement data blocks ----
        var movements = new ArrayList<AmalBank.Movement>(numMovements);
        for (int i = 0; i < numMovements; i++) {
            AmalBank.MovementData xMove = null;
            AmalBank.MovementData yMove = null;

            if (offsets[i] != 0 && lengths[i] >= 4) {
                int blockPos = movesBase + offsets[i] * 2;
                if (blockPos + 4 <= stringsStart) {
                    buf.position(blockPos);
                    int speed = buf.getShort() & 0xFFFF;
                    int nx = buf.getShort() & 0xFFFF;

                    // X step bytes: exactly nx bytes (includes leading sentinel and any trailing bytes)
                    int xLen = Math.min(nx, payload.length - buf.position());
                    var xRaw = new byte[xLen];
                    buf.get(xRaw);
                    xMove = new AmalBank.MovementData(speed, xRaw);

                    // Y step bytes: remainder of the block
                    int ny = lengths[i] - 4 - nx;
                    if (ny > 0) {
                        int yLen = Math.min(ny, payload.length - buf.position());
                        var yRaw = new byte[yLen];
                        buf.get(yRaw);
                        yMove = new AmalBank.MovementData(speed, yRaw);
                    }
                }
            }

            movements.add(new AmalBank.Movement(names[i], xMove, yMove));
        }

        // ---- Parse programs section ----
        List<String> programs = List.of();
        String environment = "";
        if (stringsStart > 0 && stringsStart < payload.length) {
            buf.position(stringsStart);
            var progResult = parsePrograms(buf, stringsStart);
            programs = progResult.programs();
            environment = progResult.environment();
        }

        return new AmalBank(hdr.bankNumber(), hdr.chipRam(), List.copyOf(movements), programs, environment);
    }

    // -------------------------------------------------------------------------
    // Programs section
    // -------------------------------------------------------------------------

    private record ProgramsResult(String environment, List<String> programs) {
    }

    private static ProgramsResult parsePrograms(ByteBuffer buf, int progsBase) {
        int numPrograms = buf.getShort() & 0xFFFF;
        var wordOffsets = new int[numPrograms];
        for (int i = 0; i < numPrograms; i++) {
            wordOffsets[i] = buf.getShort() & 0xFFFF;
        }

        // After the offset table: null guard (2 bytes, should be 0x0000) then environment program.
        String environment = "";
        if (buf.remaining() >= 2) {
            buf.getShort(); // skip null guard
            if (buf.remaining() >= 2) {
                int envLen = buf.getShort() & 0xFFFF;
                if (envLen > 0 && buf.remaining() >= envLen) {
                    var envBytes = new byte[envLen];
                    buf.get(envBytes);
                    int strLen = envLen;
                    while (strLen > 0 && envBytes[strLen - 1] == 0) strLen--;
                    environment = new String(envBytes, 0, strLen, StandardCharsets.ISO_8859_1);
                }
            }
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
        return new ProgramsResult(environment, List.copyOf(programs));
    }
}
