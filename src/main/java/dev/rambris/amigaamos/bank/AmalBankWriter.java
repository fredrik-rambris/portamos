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
import java.util.Arrays;
import java.util.List;

/**
 * Serializes an {@link AmalBank} to an AMOS Professional AMAL bank binary ({@code .Abk}).
 *
 * <p>This is the inverse of {@link AmalBankReader}. The binary layout produced:
 * <pre>
 *   [4]   "AmBk" magic
 *   [2]   bank number
 *   [2]   flags (0x0000 = chip RAM, 0x0001 = fast RAM)
 *   [4]   payload size + 8 (AmBk convention)
 *   [8]   "Amal    "
 *   --- Payload ---
 *   [4]   Strings-Start  byte offset within payload to the Programs section
 *   --- Moves section (at payload[4]) ---
 *   [2]   Number_Of_Movements
 *   [2×n] X-offsets (word offsets from payload[4]; 0 = no X movement)
 *   [2×n] Y-offsets (word offsets from payload[4]; 0 = no Y movement)
 *   [8×n] 8-byte ASCII names, space-padded
 *   --- Movement data blocks ---
 *   XMove_NN: [2] speed + [2] length + [length] data (0x00 sentinel · encoded · 0x00)
 *   YMove_NN: [0x00 sentinel] + [encoded] + [0x00]
 *   --- Programs section ---
 *   [2]   Number_Of_Programs
 *   [2×n] Word offsets from the byte after the count word; 0 = empty slot
 *   Prog_NN: [2] length (bytes following) + [n] ASCII text (~ line separator)
 *   [2]   0x0000 null placeholder (target for empty-slot offset 0)
 * </pre>
 */
public class AmalBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (bank instanceof AmalBank ab) {
            return serialize(ab);
        }
        throw new IllegalArgumentException("Not an AmalBank");
    }

    // -------------------------------------------------------------------------
    // Top-level serialization
    // -------------------------------------------------------------------------

    private byte[] serialize(AmalBank bank) {
        byte[] movesSection = buildMovesSection(bank.movements());
        byte[] progsSection = buildProgsSection(bank.programs());

        // Strings-Start = 4 (size of the Strings-Start field itself) + moves section size
        int stringsStart = 4 + movesSection.length;

        ByteBuffer payload = ByteBuffer.allocate(4 + movesSection.length + progsSection.length)
                .order(ByteOrder.BIG_ENDIAN);
        payload.putInt(stringsStart);
        payload.put(movesSection);
        payload.put(progsSection);

        return AmBkCodec.build(
                bank.bankNumber(),
                bank.chipRam(),
                AmosBank.Type.AMAL.identifier(),
                payload.array());
    }

    // -------------------------------------------------------------------------
    // Moves section
    // -------------------------------------------------------------------------

    private byte[] buildMovesSection(List<AmalBank.Movement> movements) {
        int n = movements.size();

        // Encode movement data blocks; track their positions within the moves section.
        // movesBase = 0 within the moves section (corresponds to payload[4]).
        // Table header: 2(count) + 2n(x offsets) + 2n(y offsets) + 8n(names)
        int tableHeaderSize = 2 + 4 * n + 8 * n;

        List<byte[]> xBlocks = new ArrayList<>(n);
        List<byte[]> yBlocks = new ArrayList<>(n);
        for (AmalBank.Movement mov : movements) {
            xBlocks.add(mov.xMove() != null ? encodeXMove(mov.xMove()) : null);
            yBlocks.add(mov.yMove() != null ? encodeYMove(mov.yMove()) : null);
        }

        // Compute word offsets from movesBase (= payload[4]) for each block.
        // movesBase corresponds to offset 0 within the moves section, which starts 0 bytes
        // into the moves section — but the moves section itself is placed at payload[4],
        // so all offsets are (absolute_position_in_moves_section) / 2.
        int[] xWordOffsets = new int[n];
        int[] yWordOffsets = new int[n];
        int pos = tableHeaderSize; // current write position within moves section
        for (int i = 0; i < n; i++) {
            if (xBlocks.get(i) != null) {
                xWordOffsets[i] = pos / 2;
                pos += xBlocks.get(i).length;
            }
            if (yBlocks.get(i) != null) {
                yWordOffsets[i] = pos / 2;
                pos += yBlocks.get(i).length;
            }
        }
        int totalSize = pos;

        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);

        // Count
        buf.putShort((short) n);
        // X offsets
        for (int o : xWordOffsets) buf.putShort((short) o);
        // Y offsets
        for (int o : yWordOffsets) buf.putShort((short) o);
        // Names (8 bytes each, space-padded)
        for (AmalBank.Movement mov : movements) {
            buf.put(paddedName(mov.name()));
        }
        // Movement data blocks
        for (int i = 0; i < n; i++) {
            if (xBlocks.get(i) != null) buf.put(xBlocks.get(i));
            if (yBlocks.get(i) != null) buf.put(yBlocks.get(i));
        }

        return buf.array();
    }

    /**
     * Encodes an X-axis movement: [2] speed + [2] length + [0x00] + [encoded] + [0x00].
     */
    private byte[] encodeXMove(AmalBank.MovementData data) {
        byte[] encoded = encodeInstructions(data.instructions());
        // data block: leading 0x00 sentinel + encoded bytes + trailing 0x00
        int dataLen = 1 + encoded.length + 1;
        ByteBuffer buf = ByteBuffer.allocate(4 + dataLen).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) data.speed());
        buf.putShort((short) dataLen);
        buf.put((byte) 0x00);   // leading sentinel (for backwards playback)
        buf.put(encoded);
        buf.put((byte) 0x00);   // trailing sentinel
        return buf.array();
    }

    /**
     * Encodes a Y-axis movement: [0x00] + [encoded] + [0x00] (no speed/length header).
     */
    private byte[] encodeYMove(AmalBank.MovementData data) {
        byte[] encoded = encodeInstructions(data.instructions());
        ByteBuffer buf = ByteBuffer.allocate(1 + encoded.length + 1).order(ByteOrder.BIG_ENDIAN);
        buf.put((byte) 0x00);
        buf.put(encoded);
        buf.put((byte) 0x00);
        return buf.array();
    }

    private byte[] encodeInstructions(List<AmalBank.Instruction> instructions) {
        byte[] out = new byte[instructions.size()];
        for (int i = 0; i < instructions.size(); i++) {
            out[i] = switch (instructions.get(i)) {
                case AmalBank.Instruction.Wait w  -> (byte) (0x80 | (w.ticks() & 0x7F));
                case AmalBank.Instruction.Delta d -> (byte) (d.pixels() & 0x7F);
            };
        }
        return out;
    }

    // -------------------------------------------------------------------------
    // Programs section
    // -------------------------------------------------------------------------

    private byte[] buildProgsSection(List<String> programs) {
        int n = programs.size();

        // Serialize non-empty programs; pad each to a word boundary.
        List<byte[]> progBlocks = new ArrayList<>(n);
        for (String prog : programs) {
            if (prog == null || prog.isEmpty()) {
                progBlocks.add(null);
            } else {
                byte[] textBytes = prog.getBytes(StandardCharsets.ISO_8859_1);
                // Pad to word boundary
                int padded = textBytes.length % 2 != 0 ? textBytes.length + 1 : textBytes.length;
                ByteBuffer pb = ByteBuffer.allocate(2 + padded).order(ByteOrder.BIG_ENDIAN);
                pb.putShort((short) padded);   // length = bytes following the length word
                pb.put(textBytes);
                // trailing padding byte already zero
                progBlocks.add(pb.array());
            }
        }

        // Compute word offsets. Offsets are measured from the byte AFTER the count word.
        // A null slot gets offset 0 (the null sentinel).
        // progsBase+2 in absolute terms is where offset 0 would land — pointing into the
        // offset table, which is never a valid program location, so 0 = "empty".
        int[] wordOffsets = new int[n];
        // Table: 2(count) + 2n(offsets) = 2+2n bytes; offset 0 cannot be used for real data.
        // We place real program data starting after the table (at byte 2+2n from progsBase).
        // The first real program's word offset from (progsBase+2) = (2n) / 2 = n.
        // Actually: first block sits at byte offset (2 + 2n) from progsBase, so
        //           word offset from (progsBase+2) = 2n / 2 = n.
        int pos = 2 * n; // position relative to (progsBase+2), in bytes
        for (int i = 0; i < n; i++) {
            if (progBlocks.get(i) != null) {
                wordOffsets[i] = pos / 2;
                pos += progBlocks.get(i).length;
            }
            // else wordOffsets[i] stays 0 = null sentinel
        }

        // Total: 2(count) + 2n(offsets) + program data
        // Offset=0 is the null sentinel — it points back into the offset table itself,
        // which is never a valid program location. No explicit placeholder needed.
        int totalSize = 2 + 2 * n + pos;
        ByteBuffer buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);

        buf.putShort((short) n);
        for (int o : wordOffsets) buf.putShort((short) o);
        for (byte[] block : progBlocks) {
            if (block != null) buf.put(block);
        }

        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns an 8-byte space-padded ASCII name. */
    private static byte[] paddedName(String name) {
        byte[] out = new byte[8];
        Arrays.fill(out, (byte) ' ');
        byte[] nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(nameBytes, 0, out, 0, Math.min(nameBytes.length, 8));
        return out;
    }
}
