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
 *   [2×n] Word offsets from movesBase to each movement data block (0 = empty slot)
 *   [2×n] Byte lengths of each movement data block (0 = empty slot)
 *   [8×n] 8-byte ASCII names, space-padded
 *   --- Movement data blocks ---
 *   [2]   speed
 *   [2]   n_x (number of X step bytes)
 *   [n_x] X step bytes (0x00 sentinel · encoded · 0x00)
 *   [?]   Y step bytes (encoded · 0x00 [· 0x00 alignment pad])
 *   --- Programs section ---
 *   [2]   Number_Of_Programs
 *   [2×n] Word offsets from the byte after the count word; 0 = empty slot
 *   [2]   0x0000 null placeholder (target for empty-slot offset 0)
 *   [?]   Environment program: [2] length + [n] ASCII text
 *   Prog_NN: [2] length (bytes following) + [n] ASCII text (~ line separator)
 *   [?]   Zero region for empty-slot targets
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
        throw new IllegalArgumentException("Not an AmalBank, got: " + bank.getClass().getSimpleName());
    }

    // -------------------------------------------------------------------------
    // Top-level serialization
    // -------------------------------------------------------------------------

    private byte[] serialize(AmalBank bank) {
        var movesSection = buildMovesSection(bank.movements());
        var progsSection = buildProgsSection(bank.programs(), bank.environment());

        // Strings-Start = 4 (size of the Strings-Start field itself) + moves section size
        int stringsStart = 4 + movesSection.length;

        var payload = ByteBuffer.allocate(4 + movesSection.length + progsSection.length)
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

        // Build raw data blocks for each non-empty movement.
        var blocks = new ArrayList<byte[]>(n);
        for (var mov : movements) {
            blocks.add(mov.isEmpty() ? null : buildMovementBlock(mov));
        }

        // Table header: 2(count) + 2n(word offsets) + 2n(lengths) + 8n(names)
        int tableHeaderSize = 2 + 4 * n + 8 * n;

        // Compute word offsets and byte lengths for each block.
        var wordOffsets = new int[n];
        var blockLengths = new int[n];
        int pos = tableHeaderSize;
        for (int i = 0; i < n; i++) {
            if (blocks.get(i) != null) {
                wordOffsets[i] = pos / 2;
                blockLengths[i] = blocks.get(i).length;
                pos += blocks.get(i).length;
            }
        }
        int totalSize = pos;

        var buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);

        // Count
        buf.putShort((short) n);
        // Word offsets
        for (var o : wordOffsets) buf.putShort((short) o);
        // Byte lengths
        for (var l : blockLengths) buf.putShort((short) l);
        // Names (8 bytes each, space-padded)
        for (var mov : movements) {
            buf.put(paddedName(mov.name()));
        }
        // Movement data blocks
        for (var block : blocks) {
            if (block != null) buf.put(block);
        }

        return buf.array();
    }

    /**
     * Builds a complete movement data block: [speed:2][n_x:2][x_raw][y_raw][?pad].
     *
     * <p>The block is padded to an even number of bytes so that the word-offset table remains
     * consistent; the padding byte is zero and falls after the Y step terminator.
     */
    private byte[] buildMovementBlock(AmalBank.Movement mov) {
        byte[] xRaw = mov.xMove() != null ? mov.xMove().raw() : new byte[]{0x00, 0x00};
        byte[] yRaw = mov.yMove() != null ? mov.yMove().raw() : new byte[0];

        int rawTotal = 4 + xRaw.length + yRaw.length;
        int total = (rawTotal + 1) & ~1; // round up to even for word-aligned offset table
        var buf = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        int speed = mov.xMove() != null ? mov.xMove().speed()
                : mov.yMove() != null ? mov.yMove().speed() : 1;
        buf.putShort((short) speed);
        buf.putShort((short) xRaw.length);
        buf.put(xRaw);
        buf.put(yRaw);
        // Remaining byte(s) in buf are zero-initialized (alignment padding).
        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Programs section
    // -------------------------------------------------------------------------

    private byte[] buildProgsSection(List<String> programs, String environment) {
        int n = programs.size();

        // Encode environment; always emit at least a 2-byte zero block.
        byte[] envBlock = buildProgramBlock(environment);

        // Encode non-empty channel programs.
        var progBlocks = new ArrayList<byte[]>(n);
        for (var prog : programs) {
            progBlocks.add((prog == null || prog.isEmpty()) ? null : buildProgramBlock(prog));
        }

        // Pass 1: assign word offsets to real programs; track first word of zero region.
        // pos = byte offset from (progsBase+2). Starts after: n offsets + null guard + env.
        int pos = 2 * n + 2 + envBlock.length;
        var wordOffsets = new int[n];
        for (int i = 0; i < n; i++) {
            if (i == n - 1) {
                wordOffsets[i] = 0;    // last slot: null sentinel (offset=0)
            } else if (progBlocks.get(i) != null) {
                wordOffsets[i] = pos / 2;
                pos += progBlocks.get(i).length;
            }
            // empty non-last slots: filled in pass 2
        }

        // pos is now the word offset of the start of the zero region.
        int zeroRegionWordStart = pos / 2;

        // Pass 2: assign sequential non-zero word offsets to empty non-last slots.
        int emptyCounter = 0;
        for (int i = 0; i < n - 1; i++) {
            if (progBlocks.get(i) == null) {
                wordOffsets[i] = zeroRegionWordStart + emptyCounter;
                emptyCounter++;
            }
        }

        // Zero region: emptyCounter slots (2 bytes each) + n/9 extra words of padding,
        // matching the layout produced by the AMOS editor.
        int zeroRegionBytes = emptyCounter > 0 ? emptyCounter * 2 + (n / 9) * 2 : 0;
        int totalSize = 2 + pos + zeroRegionBytes;

        var buf = ByteBuffer.allocate(totalSize).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) n);
        for (int o : wordOffsets) buf.putShort((short) o);
        buf.putShort((short) 0);    // null guard
        buf.put(envBlock);
        for (var block : progBlocks) {
            if (block != null) buf.put(block);
        }
        // Zero region is zero-initialized by Java; nothing to write.

        return buf.array();
    }

    private byte[] buildProgramBlock(String text) {
        if (text == null || text.isEmpty()) {
            return new byte[]{0, 0};
        }
        var textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        int padded = textBytes.length % 2 != 0 ? textBytes.length + 1 : textBytes.length;
        var pb = ByteBuffer.allocate(2 + padded).order(ByteOrder.BIG_ENDIAN);
        pb.putShort((short) padded);
        pb.put(textBytes);
        return pb.array();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns an 8-byte space-padded ASCII name. */
    private static byte[] paddedName(String name) {
        var out = new byte[8];
        Arrays.fill(out, (byte) ' ');
        var nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(nameBytes, 0, out, 0, Math.min(nameBytes.length, 8));
        return out;
    }
}
