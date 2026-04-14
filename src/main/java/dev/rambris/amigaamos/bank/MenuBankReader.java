/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Reads an AMOS Professional Menu bank ({@code AmBk / "Menu    "}) into a {@link MenuBank}.
 *
 * <p>The bank payload is a DFS-preorder sequence of 70-byte ({@code MnLong}) node structs.
 * Sibling items at the same level are chained via {@code MnNext}; children of a node are
 * reached via {@code MnLat}. All pointer fields ({@code MnNext}, {@code MnLat}, {@code MnObF},
 * {@code MnOb1}, {@code MnOb2}, {@code MnOb3}) are relative byte offsets from the start of the
 * payload. The {@code MnPrev} field holds an absolute Amiga address from save time and is ignored.
 *
 * <p>Object blobs immediately follow the node that references them. Each blob begins with a
 * big-endian {@code uint16} that gives its total byte length (including those two bytes).
 */
public class MenuBankReader {

    /** Size of one menu node struct in bytes. */
    static final int MN_LONG = 70;

    public static MenuBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        var payload = hdr.payload();
        var buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);

        List<MenuNode> items = readChain(buf, 0);
        return new MenuBank(hdr.bankNumber(), hdr.chipRam(), List.copyOf(items));
    }

    /**
     * Reads the {@code MnNext} sibling chain starting at {@code startOffset}, recursively
     * following each node's {@code MnLat} chain to build the children list.
     */
    static List<MenuNode> readChain(ByteBuffer buf, int startOffset) {
        List<MenuNode> result = new ArrayList<>();
        Set<Integer> visited = new HashSet<>();

        int offset = startOffset;
        // Note: offset == 0 is valid for the root node; 0 as MnNext means "no more siblings"
        // so we stop by breaking when mnNext == 0, not by the initial offset check.
        while (offset >= 0 && offset < buf.capacity() && visited.add(offset)) {
            buf.position(offset);

            /* @formatter:off */
            int mnPrev    = buf.getInt();              // +0  – absolute Amiga addr; ignore
            int mnNext    = buf.getInt();              // +4  – relative offset to next sibling
            int mnLat     = buf.getInt();              // +8  – relative offset to first child
            int mnNb      = buf.getShort() & 0xFFFF;  // +12
            int mnFlag    = buf.getShort() & 0xFFFF;  // +14
            int mnX       = buf.getShort();            // +16
            int mnY       = buf.getShort();            // +18
            int mnTx      = buf.getShort();            // +20
            int mnTy      = buf.getShort();            // +22
            int mnMX      = buf.getShort();            // +24
            int mnMY      = buf.getShort();            // +26
            int mnXX      = buf.getShort();            // +28
            int mnYY      = buf.getShort();            // +30
            int mnZone    = buf.getShort() & 0xFFFF;  // +32
            int mnKFlag   = buf.get() & 0xFF;          // +34
            int mnKAsc    = buf.get() & 0xFF;          // +35
            int mnKSc     = buf.get() & 0xFF;          // +36
            int mnKSh     = buf.get() & 0xFF;          // +37
            int mnObF     = buf.getInt();              // +38 – relative offset to font object
            int mnOb1     = buf.getInt();              // +42 – relative offset to normal object
            int mnOb2     = buf.getInt();              // +46 – relative offset to selected object
            int mnOb3     = buf.getInt();              // +50 – relative offset to inactive object
            int mnAdSave  = buf.getInt();              // +54 – runtime; preserved as-is
            int mnDatas   = buf.getInt();              // +58 – runtime; preserved as-is
            int mnLData   = buf.getShort() & 0xFFFF;  // +62
            int mnInkA1   = buf.get() & 0xFF;          // +64
            int mnInkB1   = buf.get() & 0xFF;          // +65
            int mnInkC1   = buf.get() & 0xFF;          // +66
            int mnInkA2   = buf.get() & 0xFF;          // +67
            int mnInkB2   = buf.get() & 0xFF;          // +68
            int mnInkC2   = buf.get() & 0xFF;          // +69
            /* @formatter:on */

            // Read object blobs (opaque; first word = total byte length incl. itself)
            byte[] fontObj     = readObject(buf, mnObF);
            byte[] normalObj   = readObject(buf, mnOb1);
            byte[] selectedObj = readObject(buf, mnOb2);
            byte[] inactiveObj = readObject(buf, mnOb3);

            // Recurse into children via MnLat
            List<MenuNode> children = (mnLat != 0) ? readChain(buf, mnLat) : List.of();

            result.add(new MenuNode(
                    mnNb, mnFlag,
                    mnX, mnY, mnTx, mnTy, mnMX, mnMY, mnXX, mnYY,
                    mnZone,
                    mnKFlag, mnKAsc, mnKSc, mnKSh,
                    fontObj, normalObj, selectedObj, inactiveObj,
                    mnAdSave, mnDatas, mnLData,
                    mnInkA1, mnInkB1, mnInkC1,
                    mnInkA2, mnInkB2, mnInkC2,
                    children
            ));

            if (mnNext == 0) break;  // end of sibling chain
            offset = mnNext;         // advance to next sibling
        }

        return result;
    }

    /**
     * Reads one object blob at the given payload offset, or returns {@code null} if the offset
     * is zero or out-of-bounds. The blob starts with a big-endian uint16 giving the total byte
     * count (including itself).
     */
    private static byte[] readObject(ByteBuffer buf, int offset) {
        if (offset == 0 || offset < 0 || offset >= buf.capacity()) return null;
        buf.position(offset);
        if (buf.remaining() < 2) return null;
        int size = buf.getShort() & 0xFFFF;
        if (size < 2 || offset + size > buf.capacity()) return null;
        byte[] data = new byte[size];
        buf.position(offset);
        buf.get(data);
        return data;
    }
}
