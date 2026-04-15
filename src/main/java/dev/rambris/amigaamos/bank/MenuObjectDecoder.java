/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Decodes an AMOS Professional menu object blob to a human-readable string in the
 * AMOS {@code Menu$()} embedded-command notation.
 *
 * <p>Object blobs are sequences of 16-bit command codes followed by their parameters.
 * The blob begins with a big-endian {@code uint16} giving the total byte length
 * (including those two bytes). Commands continue until a {@code 0x0000} end marker.
 *
 * <p>The command dispatch table (from {@code +Lib.s:ObJumps}) maps byte offsets to handlers:
 * <pre>
 *   0x0000  END           (no params)
 *   0x0004  TEXT          strlen:2 + text_bytes + pad_if_strlen_odd
 *   0x0008  BAr           x:2, y:2     → (BA x,y)
 *   0x000C  LIne          x:2, y:2     → (LI x,y)
 *   0x0010  ELlipse       rx:2, ry:2   → (EL rx,ry)
 *   0x0014  PAttern       n:2          → (PAn)
 *   0x0018  INk           mode:2, v:2  → (INmode,v)
 *   0x001C  BOb           n:2          → (BOn)
 *   0x0020  ICon          n:2          → (ICn)
 *   0x0024  LOcate        x:2, y:2     → (LO x,y)
 *   0x0028  OUtline       n:2          → (OUn)
 *   0x002C  SLine         n:2          → (SLn)
 *   0x0030  SFont         n:2          → (SFn)
 *   0x0034  PRoc          delta:2 + delta_bytes (variable; procedure call)
 *   0x0038  REserve       n:2          → (REn)
 *   0x003C  SStyle        n:2          → (SSn)
 * </pre>
 *
 * <p>Text is embedded directly in the returned string; unknown commands are rendered
 * as {@code [??XXXX]} hex escapes to allow lossless round-tripping via
 * {@link MenuObjectEncoder}.
 *
 * @see MenuObjectEncoder
 */
public class MenuObjectDecoder {

    private MenuObjectDecoder() {}

    /**
     * Decodes the given blob to a Menu$ string.
     *
     * @param blob the raw object blob (first two bytes = total size), or {@code null}
     * @return the decoded string, or {@code null} if {@code blob} is {@code null}
     */
    public static String decode(byte[] blob) {
        if (blob == null || blob.length < 2) return null;

        var buf = ByteBuffer.wrap(blob).order(ByteOrder.BIG_ENDIAN);
        buf.getShort(); // skip total-size word

        var sb = new StringBuilder();

        outer:
        while (buf.remaining() >= 2) {
            int code = buf.getShort() & 0xFFFF;
            switch (code) {
                case 0x0000 -> { break outer; }             // END
                case 0x0004 -> readText(buf, sb);           // TEXT
                case 0x0008 -> twoSigned(buf, sb, "BA");    // BAr
                case 0x000C -> twoSigned(buf, sb, "LI");    // LIne
                case 0x0010 -> twoSigned(buf, sb, "EL");    // ELlipse
                case 0x0014 -> oneSigned(buf, sb, "PA");    // PAttern
                case 0x0018 -> twoNoSpace(buf, sb, "IN");   // INk
                case 0x001C -> oneSigned(buf, sb, "BO");    // BOb
                case 0x0020 -> oneSigned(buf, sb, "IC");    // ICon
                case 0x0024 -> twoSigned(buf, sb, "LO");    // LOcate
                case 0x0028 -> oneSigned(buf, sb, "OU");    // OUtline
                case 0x002C -> oneSigned(buf, sb, "SL");    // SLine
                case 0x0030 -> oneSigned(buf, sb, "SF");    // SFont
                case 0x0034 -> readProc(buf, sb);           // PRoc
                case 0x0038 -> oneSigned(buf, sb, "RE");    // REserve
                case 0x003C -> oneSigned(buf, sb, "SS");    // SStyle
                default     -> { appendUnknown(buf, code, sb); break outer; }
            }
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Reads the TEXT command payload and appends the raw text characters. */
    private static void readText(ByteBuffer buf, StringBuilder sb) {
        if (buf.remaining() < 2) return;
        int len = buf.getShort() & 0xFFFF;
        if (buf.remaining() < len) return;
        var bytes = new byte[len];
        buf.get(bytes);
        sb.append(new String(bytes, StandardCharsets.ISO_8859_1));
        if ((len & 1) == 1 && buf.hasRemaining()) buf.get(); // skip pad byte
    }

    /** One signed 16-bit param: {@code (XXn)} with no space. */
    private static void oneSigned(ByteBuffer buf, StringBuilder sb, String mnemonic) {
        if (buf.remaining() < 2) return;
        sb.append('(').append(mnemonic).append(buf.getShort()).append(')');
    }

    /**
     * Two signed 16-bit params with a separating space: {@code (XX p1,p2)}.
     * Coordinate-bearing commands (BA, LI, EL, LO) follow this convention in AMOS source.
     */
    private static void twoSigned(ByteBuffer buf, StringBuilder sb, String mnemonic) {
        if (buf.remaining() < 4) return;
        int p1 = buf.getShort();
        int p2 = buf.getShort();
        sb.append('(').append(mnemonic).append(' ').append(p1).append(',').append(p2).append(')');
    }

    /**
     * Two signed 16-bit params with no space: {@code (XXp1,p2)}.
     * The INk command uses this convention in AMOS source.
     */
    private static void twoNoSpace(ByteBuffer buf, StringBuilder sb, String mnemonic) {
        if (buf.remaining() < 4) return;
        int p1 = buf.getShort();
        int p2 = buf.getShort();
        sb.append('(').append(mnemonic).append(p1).append(',').append(p2).append(')');
    }

    /**
     * Reads the PRoc (procedure-call) command.
     * Format: {@code delta:2 + delta_bytes} where the bytes hold the procedure name
     * as a length-prefixed, even-padded string.
     */
    private static void readProc(ByteBuffer buf, StringBuilder sb) {
        if (buf.remaining() < 2) return;
        int delta = buf.getShort() & 0xFFFF;
        if (buf.remaining() < delta) return;
        if (delta >= 2) {
            int nameLen = buf.getShort() & 0xFFFF;
            int consumed = 2;
            if (nameLen > 0 && buf.remaining() >= nameLen) {
                var nameBytes = new byte[nameLen];
                buf.get(nameBytes);
                consumed += nameLen;
                var name = new String(nameBytes, StandardCharsets.ISO_8859_1)
                        .replace("\0", ""); // strip null terminator
                sb.append("(PR ").append(name).append(')');
                if (consumed < delta) buf.position(buf.position() + (delta - consumed));
                return;
            }
            buf.position(buf.position() + (delta - consumed));
        } else {
            buf.position(buf.position() + delta);
        }
        sb.append("(PR)");
    }

    /** Appends an unrecognised command as a {@code [??XXXX]} hex escape. */
    private static void appendUnknown(ByteBuffer buf, int code, StringBuilder sb) {
        sb.append(String.format("[??%04X]", code));
    }
}
