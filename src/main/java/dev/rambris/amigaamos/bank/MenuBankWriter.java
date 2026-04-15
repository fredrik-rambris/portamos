/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Serialises a {@link MenuBank} to an AMOS Professional Menu bank binary.
 *
 * <p>This is the inverse of {@link MenuBankReader}.
 *
 * <p>The payload is built in DFS pre-order: for each node the 70-byte struct is written first,
 * immediately followed by its object blobs, then its children's sub-tree, then the next sibling's
 * sub-tree. Relative offset fields ({@code MnNext}, {@code MnLat}, {@code MnObF}–{@code MnOb3})
 * are filled in retroactively using {@link ByteBuffer#putInt(int, int)} once the target position
 * is known. {@code MnPrev} is always written as zero — AMOS reconstructs it at runtime.
 */
public class MenuBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (!(bank instanceof MenuBank mb)) {
            throw new IllegalArgumentException("Not a MenuBank, got: " + bank.getClass().getSimpleName());
        }

        int payloadSize = calculateSize(mb.items());
        var buf = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN);

        writeChain(buf, mb.items());

        return AmBkCodec.build(
                mb.bankNumber(),
                mb.chipRam(),
                AmosBank.Type.MENU.identifier(),
                buf.array());
    }

    // -------------------------------------------------------------------------
    // Size calculation
    // -------------------------------------------------------------------------

    private static int calculateSize(List<MenuNode> items) {
        int size = 0;
        for (var node : items) {
            size += MenuBankReader.MN_LONG;
            size += objectSize(node.fontObject());
            size += objectSize(node.normalObject());
            size += objectSize(node.selectedObject());
            size += objectSize(node.inactiveObject());
            size += calculateSize(node.children());
        }
        return size;
    }

    private static int objectSize(byte[] obj) {
        return (obj == null || obj.length < 2) ? 0 : obj.length;
    }

    // -------------------------------------------------------------------------
    // Writing
    // -------------------------------------------------------------------------

    /**
     * Writes a sibling chain (and all their descendants) into {@code buf} in DFS pre-order.
     */
    private static void writeChain(ByteBuffer buf, List<MenuNode> items) {
        for (int i = 0; i < items.size(); i++) {
            var node = items.get(i);
            int nodeStart = buf.position();

            // --- Write 70-byte node struct (MnLong) ---
            buf.putInt(0);                              // +0  MnPrev – always 0
            buf.putInt(0);                              // +4  MnNext – filled later
            buf.putInt(0);                              // +8  MnLat  – filled later
            buf.putShort((short) node.itemNumber());    // +12 MnNb
            buf.putShort((short) node.flags());         // +14 MnFlag
            buf.putShort((short) node.x());             // +16 MnX
            buf.putShort((short) node.y());             // +18 MnY
            buf.putShort((short) node.textX());         // +20 MnTx
            buf.putShort((short) node.textY());         // +22 MnTy
            buf.putShort((short) node.maxX());          // +24 MnMX
            buf.putShort((short) node.maxY());          // +26 MnMY
            buf.putShort((short) node.xx());            // +28 MnXX
            buf.putShort((short) node.yy());            // +30 MnYY
            buf.putShort((short) node.zone());          // +32 MnZone
            buf.put((byte) node.keyFlag());             // +34 MnKFlag
            buf.put((byte) node.keyAscii());            // +35 MnKAsc
            buf.put((byte) node.keyScancode());         // +36 MnKSc
            buf.put((byte) node.keyShift());            // +37 MnKSh
            buf.putInt(0);                              // +38 MnObF  – filled later
            buf.putInt(0);                              // +42 MnOb1  – filled later
            buf.putInt(0);                              // +46 MnOb2  – filled later
            buf.putInt(0);                              // +50 MnOb3  – filled later
            buf.putInt(node.adSave());                  // +54 MnAdSave
            buf.putInt(node.datas());                   // +58 MnDatas
            buf.putShort((short) node.lData());         // +62 MnLData
            buf.put((byte) node.inkA1());               // +64
            buf.put((byte) node.inkB1());               // +65
            buf.put((byte) node.inkC1());               // +66
            buf.put((byte) node.inkA2());               // +67
            buf.put((byte) node.inkB2());               // +68
            buf.put((byte) node.inkC2());               // +69

            // --- Write object blobs and back-patch offset fields ---
            writeObject(buf, node.fontObject(),     nodeStart + 38);
            writeObject(buf, node.normalObject(),   nodeStart + 42);
            writeObject(buf, node.selectedObject(), nodeStart + 46);
            writeObject(buf, node.inactiveObject(), nodeStart + 50);

            // --- Write children subtree and back-patch MnLat ---
            if (!node.children().isEmpty()) {
                int childrenStart = buf.position();
                buf.putInt(nodeStart + 8, childrenStart);  // MnLat
                writeChain(buf, node.children());
            }

            // --- Back-patch MnNext to point to the next sibling ---
            if (i + 1 < items.size()) {
                int nextStart = buf.position();
                buf.putInt(nodeStart + 4, nextStart);  // MnNext
            }
        }
    }

    /**
     * Appends an object blob to the buffer and back-patches the offset field in the node struct.
     * Does nothing if the blob is {@code null} or too short to be valid.
     */
    private static void writeObject(ByteBuffer buf, byte[] obj, int offsetFieldPos) {
        if (obj == null || obj.length < 2) return;
        int objOffset = buf.position();
        buf.putInt(offsetFieldPos, objOffset);  // back-patch the relative offset
        buf.put(obj);
    }
}
