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

/**
 * Reads an AMOS Professional Bank Set file ({@code .Abs}).
 *
 * <p>The file begins with the 6-byte {@code AmBs} header:
 * <pre>
 *   dc.b  "AmBs"    ; 4 bytes magic
 *   dc.w  count     ; 2 bytes bank count
 * </pre>
 * followed by {@code count} individual bank blobs (AmBk, AmSp, or AmIc).
 */
public class BankSetReader {

    public static BankSet read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static BankSet read(byte[] raw) throws IOException {
        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        var magicBytes = new byte[4];
        buf.get(magicBytes);
        if (!"AmBs".equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBs bank-set file");
        }

        var count = buf.getShort() & 0xFFFF;
        var banks = new ArrayList<AmosBank>(count);

        for (int i = 0; i < count; i++) {
            if (buf.remaining() < 4) throw new IOException("Truncated AmBs file at bank " + i);

            var bankBytes = nextBankBytes(buf);
            banks.add(AmosBank.read(bankBytes));
        }

        return new BankSet(banks);
    }

    /**
     * Extracts the raw bytes of the next bank starting at the current buffer position,
     * advances the position past the bank, and returns the bytes.
     */
    private static byte[] nextBankBytes(ByteBuffer buf) throws IOException {
        var pos = buf.position();
        var remaining = buf.remaining();
        if (remaining < 4) throw new IOException("Cannot read bank magic");

        var magic = new String(buf.array(), pos, 4, StandardCharsets.US_ASCII);
        var bankSize = switch (magic) {
            case "AmBk" -> amBkSize(buf.array(), pos);
            case "AmSp", "AmIc" -> amSpSize(buf.array(), pos);
            default -> throw new IOException("Unknown bank magic in bank set: \"" + magic + "\"");
        };

        if (remaining < bankSize) {
            throw new IOException("Truncated bank data (need " + bankSize + ", have " + remaining + ")");
        }

        var bankBytes = Arrays.copyOfRange(buf.array(), pos, pos + bankSize);
        buf.position(pos + bankSize);
        return bankBytes;
    }

    /**
     * Computes the byte length of an AmBk bank starting at {@code offset} in {@code raw}.
     * Total = 12 (fixed header) + nameAndPayload (with bit 31 cleared).
     */
    private static int amBkSize(byte[] raw, int offset) throws IOException {
        if (raw.length - offset < 12) throw new IOException("Truncated AmBk header");
        var buf = ByteBuffer.wrap(raw, offset + 8, 4).order(ByteOrder.BIG_ENDIAN);
        var nameAndPayload = buf.getInt() & 0x7FFFFFFF;
        return 12 + nameAndPayload;
    }

    /**
     * Computes the byte length of an AmSp/AmIc bank starting at {@code offset} in {@code raw}.
     * Scans the per-sprite records to accumulate the total size.
     */
    private static int amSpSize(byte[] raw, int offset) throws IOException {
        if (raw.length - offset < 6) throw new IOException("Truncated AmSp/AmIc header");
        var buf = ByteBuffer.wrap(raw, offset, raw.length - offset).order(ByteOrder.BIG_ENDIAN);
        buf.position(4); // skip magic
        var count = buf.getShort() & 0xFFFF;
        for (int i = 0; i < count; i++) {
            if (buf.remaining() < 10) throw new IOException("Truncated AmSp sprite header at sprite " + i);
            var widthWords = buf.getShort() & 0xFFFF;
            var height     = buf.getShort() & 0xFFFF;
            var planes     = buf.getShort() & 0xFFFF;
            buf.getShort(); // hotspotX
            buf.getShort(); // hotspotY
            if (widthWords != 0 || height != 0 || planes != 0) {
                var pixelBytes = widthWords * 2 * height * planes;
                if (buf.remaining() < pixelBytes) throw new IOException("Truncated AmSp pixel data");
                buf.position(buf.position() + pixelBytes);
            }
        }
        // 32-entry palette
        if (buf.remaining() < 64) throw new IOException("Truncated AmSp palette");
        buf.position(buf.position() + 64);
        return buf.position();
    }
}
