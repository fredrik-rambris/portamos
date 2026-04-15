/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosLine;
import dev.rambris.amigaamos.tokenizer.model.AmosToken;
import dev.rambris.amigaamos.tokenizer.model.AmosToken.VarType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Decodes AMOS binary line bytes into {@link AmosLine} / {@link AmosToken} objects.
 *
 * <p>This is the inverse of {@link BinaryEncoder}: given the raw byte array of a
 * single encoded line (as produced by {@link BinaryEncoder#encodeLine}), it reconstructs
 * the indent level and the ordered token sequence.
 *
 * <p>Unknown keyword tokens (values not in the token table) are emitted as
 * {@link AmosToken.Keyword} with the raw 2-byte value preserved; the printer can
 * fall back to a hex annotation for these.
 */
class BinaryDecoder {

    private final TokenTable tokenTable;

    BinaryDecoder(TokenTable tokenTable) {
        this.tokenTable = tokenTable;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Decodes a single binary line into an {@link AmosLine}.
     *
     * @param line the complete binary line including the 2-byte header
     *             (wordCount byte + indent byte)
     */
    AmosLine decodeLine(byte[] line) {
        int indent = line[1] & 0xFF;
        var tokens = decodeTokens(line);
        return new AmosLine(indent, tokens);
    }

    // -------------------------------------------------------------------------
    // Token stream decoding
    // -------------------------------------------------------------------------

    /**
     * Reads the token stream from a binary line, starting after the 2-byte header.
     * Stops at the 0x0000 end-of-line token or at end of data.
     */
    List<AmosToken> decodeTokens(byte[] line) {
        var tokens = new ArrayList<AmosToken>();
        int pos = 2; // skip wordCount + indent
        while (pos + 1 < line.length) {
            int tok = readU16(line, pos);
            if (tok == 0x0000) break; // EOL

            int ps = pos + 2; // payload start (right after the 2-byte token type)

            switch (tok) {
                case 0x003E -> { // DecimalInt
                    tokens.add(new AmosToken.DecimalInt(readS32(line, ps)));
                    pos += 6;
                }
                case 0x0036 -> { // HexInt
                    tokens.add(new AmosToken.HexInt(readS32(line, ps)));
                    pos += 6;
                }
                case 0x001E -> { // BinaryInt
                    tokens.add(new AmosToken.BinaryInt(readS32(line, ps)));
                    pos += 6;
                }
                case 0x0046 -> { // Flt (AMOS custom float)
                    tokens.add(new AmosToken.Flt(amosToFloat(readU32(line, ps))));
                    pos += 6;
                }
                case 0x2B6A -> { // Dbl (IEEE 754 double)
                    tokens.add(new AmosToken.Dbl(Double.longBitsToDouble(readU64(line, ps))));
                    pos += 10;
                }
                case 0x0026 -> { // DoubleQuoteString
                    int len = readU16(line, ps);
                    tokens.add(new AmosToken.DoubleQuoteString(readStr(line, ps + 2, len)));
                    pos += 2 + 2 + len + (len % 2 != 0 ? 1 : 0);
                }
                case 0x002E -> { // SingleQuoteString
                    int len = readU16(line, ps);
                    tokens.add(new AmosToken.SingleQuoteString(readStr(line, ps + 2, len)));
                    pos += 2 + 2 + len + (len % 2 != 0 ? 1 : 0);
                }
                case 0x064A -> { // Rem keyword comment
                    int len = line[ps + 1] & 0xFF;
                    tokens.add(new AmosToken.Rem(readStr(line, ps + 2, len)));
                    int total = 2 + len;
                    pos += 2 + total + (total % 2 != 0 ? 1 : 0);
                }
                case 0x0652 -> { // SingleQuoteRem
                    int len = line[ps + 1] & 0xFF;
                    tokens.add(new AmosToken.SingleQuoteRem(readStr(line, ps + 2, len)));
                    int total = 2 + len;
                    pos += 2 + total + (total % 2 != 0 ? 1 : 0);
                }
                case 0x0006 -> { // Variable
                    var result = decodeNamedToken(line, ps);
                    int flags = result.flags();
                    var type = switch (flags & 0x03) {
                        case 0x01 -> VarType.FLOAT;
                        case 0x02 -> VarType.STRING;
                        default -> VarType.INTEGER;
                    };
                    boolean isArray = (flags & 0x40) != 0;
                    int extraFlags = flags & ~0x43; // strip known type + array bits
                    tokens.add(new AmosToken.Variable(result.name(), type, isArray, extraFlags));
                    pos += 2 + 4 + result.n();
                }
                case 0x000C -> { // Label definition
                    var result = decodeNamedToken(line, ps);
                    tokens.add(new AmosToken.Label(result.name()));
                    pos += 2 + 4 + result.n();
                }
                case 0x0012 -> { // ProcRef
                    var result = decodeNamedToken(line, ps);
                    tokens.add(new AmosToken.ProcRef(result.name()));
                    pos += 2 + 4 + result.n();
                }
                case 0x0018 -> { // LabelRef
                    var result = decodeNamedToken(line, ps);
                    tokens.add(new AmosToken.LabelRef(result.name()));
                    pos += 2 + 4 + result.n();
                }
                case 0x004E -> { // ExtKeyword
                    int slot = line[ps] & 0xFF;
                    int offset = readU16(line, ps + 2);
                    tokens.add(new AmosToken.ExtKeyword(slot, offset));
                    pos += 6; // 2 (type) + 1 (slot) + 1 (unused) + 2 (offset)
                }
                default -> { // Plain keyword; skip any extra back-patch bytes
                    int extra = tokenTable.extraBytesFor(tok);
                    tokens.add(new AmosToken.Keyword(tok));
                    pos += 2 + extra;
                }
            }
        }
        return tokens;
    }

    // -------------------------------------------------------------------------
    // Named-token helper
    // -------------------------------------------------------------------------

    private record NamedTokenResult(String name, int flags, int n) {
    }

    /**
     * Reads a named-token payload (Variable / Label / ProcRef / LabelRef).
     * <p>
     * Layout after the 2-byte type: [unk1:1][unk2:1][n:1][flags:1][name:n bytes]
     * {@code n} is the name length rounded up to the next even number; for odd-length
     * names the extra byte is a null terminator.
     */
    private static NamedTokenResult decodeNamedToken(byte[] line, int ps) {
        // ps+0 = unk1, ps+1 = unk2, ps+2 = n, ps+3 = flags, ps+4..ps+3+n = name
        int n = ps + 2 < line.length ? (line[ps + 2] & 0xFF) : 0;
        int flags = ps + 3 < line.length ? (line[ps + 3] & 0xFF) : 0;
        var name = readStr(line, ps + 4, n);
        return new NamedTokenResult(name, flags, n);
    }

    // -------------------------------------------------------------------------
    // AMOS float ↔ Java float
    // -------------------------------------------------------------------------

    /**
     * Converts an AMOS custom single-precision float to a Java {@code float}.
     * <p>
     * AMOS format: bits 31-8 = 24-bit mantissa (MSB always set for non-zero),
     * bits 6-0 = exponent, bit 7 unused.
     * {@code value = mantissa × 2^(exponent − 88)}
     */
    static float amosToFloat(int amosBits) {
        int exponent = amosBits & 0x7F;
        if (exponent == 0) return 0.0f;
        int mantissa = (amosBits >>> 8) & 0xFFFFFF;
        return (float) (mantissa * Math.pow(2.0, exponent - 88));
    }

    // -------------------------------------------------------------------------
    // Low-level read helpers
    // -------------------------------------------------------------------------

    static int readS32(byte[] d, int o) {
        return (d[o] << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    static int readU32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16) | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }

    static long readU64(byte[] d, int o) {
        return ((long) (readU32(d, o)) << 32) | (readU32(d, o + 4) & 0xFFFFFFFFL);
    }

    static int readU16(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    /**
     * Reads up to {@code maxLen} bytes as an ASCII string, stopping at the first NUL byte.
     */
    static String readStr(byte[] d, int o, int maxLen) {
        int end = Math.min(o + maxLen, d.length);
        int actual = o;
        while (actual < end && d[actual] != 0) actual++;
        return new String(d, o, actual - o, StandardCharsets.US_ASCII);
    }
}
