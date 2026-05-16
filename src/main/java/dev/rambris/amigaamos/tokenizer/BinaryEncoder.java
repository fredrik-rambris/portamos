/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Encodes a sequence of AmosTokens into the AMOS Professional binary line format.
 *
 * Line structure:
 *   Byte 0:      line length in 2-byte words (covers entire line including header and EOL)
 *   Byte 1:      indent level
 *   Bytes 2+:    sequence of encoded tokens
 *   Last 2 bytes: 0x0000 (end-of-line token)
 *
 * All multi-byte integers are big-endian.
 */
class BinaryEncoder {

    private static final int TOK_PROCEDURE = 0x0376;

    private final TokenTable tokenTable;

    // Per-procedure-scope variable slot assignment (reset on each Procedure keyword).
    // Maps lowercase variable name → slot byte offset (= slot_index * 6).
    // AMOS assigns slots in order of first appearance in the token stream.
    private final Map<String, Integer> varSlots = new LinkedHashMap<>();
    private int nextVarSlot = 0;

    BinaryEncoder(TokenTable tokenTable) {
        this.tokenTable = tokenTable;
    }

    /** Returns the total symbol-table byte size for the current procedure scope (= varCount * 6). */
    int scopeVarTableSize() {
        return nextVarSlot * 6;
    }

    /**
     * Encodes a single source line into AMOS binary format.
     *
     * @param indent the indent level (0 = top level)
     * @param tokens the tokens on this line (not including the implicit EOL)
     * @return the complete binary representation of the line
     */
    byte[] encodeLine(int indent, List<AmosToken> tokens) {
        // AMOS stores blank lines with indent=0 regardless of context
        if (tokens.isEmpty()) indent = 0;

        // Reset per-procedure variable-slot assignment when entering a new procedure scope
        for (var t : tokens) {
            if (t instanceof AmosToken.Keyword k && k.value() == TOK_PROCEDURE) {
                varSlots.clear();
                nextVarSlot = 0;
                break;
            }
        }

        var body = new ByteArrayOutputStream();

        for (var token : tokens) {
            try {
                encodeToken(token, body);
            } catch (IOException e) {
                throw new RuntimeException("Failed to encode token: " + token, e);
            }
        }

        // EOL marker: 0x0000
        var bodyBytes = body.toByteArray();

        // Total = 2 (header) + bodyBytes.length + 2 (EOL)
        int totalBytes = 2 + bodyBytes.length + 2;
        // Must be even (should always be since we pad odd-length fields)
        if (totalBytes % 2 != 0) {
            throw new IllegalStateException("Line byte count is odd: " + totalBytes);
        }
        int totalWords = totalBytes / 2;

        var line = new ByteArrayOutputStream(totalBytes);
        line.write(totalWords);
        line.write(indent);
        try {
            line.write(bodyBytes);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        line.write(0x00); // EOL high byte
        line.write(0x00); // EOL low byte

        return line.toByteArray();
    }

    private void encodeToken(AmosToken token, ByteArrayOutputStream out) throws IOException {
        switch (token) {
            case AmosToken.SingleQuoteRem rem -> encodeRem(0x0652, rem.text(), out);
            case AmosToken.Rem rem -> encodeRem(0x064A, rem.text(), out);
            case AmosToken.DoubleQuoteString s -> encodeQuotedString(0x0026, s.text(), out);
            case AmosToken.SingleQuoteString s -> encodeQuotedString(0x002E, s.text(), out);
            case AmosToken.DecimalInt i -> encodeInt(0x003E, i.value(), out);
            case AmosToken.HexInt i -> encodeInt(0x0036, i.value(), out);
            case AmosToken.BinaryInt i -> encodeInt(0x001E, i.value(), out);
            case AmosToken.Flt f -> encodeFloat(f.value(), out);
            case AmosToken.Dbl d -> encodeDouble(d.value(), out);
            case AmosToken.Variable v -> encodeNamedToken(0x0006, v.name(), 0x00, varFlags(v), out);
            case AmosToken.Label l -> encodeNamedToken(0x000C, l.name(), 0x00, 0x00, out);
            case AmosToken.ProcRef p -> encodeNamedToken(0x0012, p.name(), 0xFF, 0x80, out);
            case AmosToken.LabelRef l -> encodeNamedToken(0x0018, l.name(), 0x00, 0x00, out);
            case AmosToken.Keyword k -> encodeKeyword(k.value(), out);
            case AmosToken.ExtKeyword e -> encodeExtKeyword(e.slot(), e.offset(), out);
        }
    }

    /**
     * Encodes a REM-style token (SingleQuoteRem or Rem keyword).
     *
     * Format: [token:2] [unused:00] [paddedLen:1] [text:n] [pad if n odd:00]
     *
     * paddedLen = n rounded up to the nearest even number.
     * AMOS VerRem uses add.w (a6)+,a6 to skip the text; the padded length
     * ensures the pointer lands correctly on the next line for any text length.
     */
    private void encodeRem(int tokenValue, String text, ByteArrayOutputStream out) throws IOException {
        var textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        int n = textBytes.length;
        // AMOS's VerRem skips via: add.w (a6)+,a6; addq.l #2,a6
        // which adds (unused<<8|len) to the text-start pointer then +2.
        // For the arithmetic to land on the next line, len must be the
        // even-padded text length, not the raw char count.
        int paddedN = (n % 2 == 0) ? n : n + 1;
        writeUint16(tokenValue, out);
        out.write(0x00);     // unused byte
        out.write(paddedN);  // even-padded text length
        out.write(textBytes);
        if (n % 2 != 0) {
            out.write(0x00); // null / alignment pad
        }
    }

    /**
     * Encodes a quoted string token (double-quote or single-quote string).
     *
     * Format: [token:2] [len_hi:1] [len_lo:1] [text:n] [pad if n odd:00]
     */
    private void encodeQuotedString(int tokenValue, String text, ByteArrayOutputStream out) throws IOException {
        var textBytes = text.getBytes(StandardCharsets.ISO_8859_1);
        int n = textBytes.length;
        writeUint16(tokenValue, out);
        writeUint16(n, out); // 2-byte big-endian length
        out.write(textBytes);
        if (n % 2 != 0) {
            out.write(0x00); // padding
        }
    }

    /**
     * Encodes an integer token (decimal, hex, or binary).
     *
     * Format: [token:2] [value:4 big-endian signed int32]
     */
    private void encodeInt(int tokenValue, int value, ByteArrayOutputStream out) throws IOException {
        writeUint16(tokenValue, out);
        writeInt32(value, out);
    }

    /**
     * Encodes a single-precision float in AMOS custom format.
     *
     * AMOS float: bits 31-8 = mantissa (24 bits, MSB always set for non-zero),
     *             bits 6-0 = exponent, bit 7 = ignored
     *
     * value = mantissa * 2^(exponent - 88)  (for exponent != 0)
     * value = 0.0  (for exponent == 0)
     *
     * Format: [0046:2] [value:4 big-endian]
     */
    private void encodeFloat(float value, ByteArrayOutputStream out) throws IOException {
        writeUint16(0x0046, out);
        writeInt32(floatToAmos(value), out);
    }

    /**
     * Encodes a double-precision float.
     * AMOS double is IEEE 754 double format (same as Java), written big-endian.
     *
     * Format: [2B6A:2] [value:8 big-endian]
     */
    private void encodeDouble(double value, ByteArrayOutputStream out) throws IOException {
        writeUint16(0x2B6A, out);
        var bits = Double.doubleToRawLongBits(value);
        writeUint64(bits, out);
    }

    /**
     * Encodes a named token: Variable, Label, ProcRef, or LabelRef.
     *
     * Format: [token:2] [00:1] [n+2:1] [n:1] [flags:1] [name:nameLen] [null if nameLen odd]
     *
     * n = nameLen rounded up to even (null terminator for odd-length names, else nameLen).
     * The second byte (n+2) is the byte count from itself to the end of the record,
     * i.e. 1 (n byte) + 1 (flags byte) + n (name bytes) = n+2. AMOS uses this to skip
     * over the variable record without knowing the name length upfront.
     * Name is stored in lowercase.
     *
     * @param unk1  0xFF for ProcRef (AMOS's "unresolved" marker, patched at load time), 0x00 otherwise
     */
    private void encodeNamedToken(int tokenValue, String name, int unk1, int flags,
                                  ByteArrayOutputStream out) throws IOException {
        var lowerName = name.toLowerCase();
        var nameBytes = lowerName.getBytes(StandardCharsets.ISO_8859_1);
        int nameLen = nameBytes.length;
        // n = nameLen rounded up to the next even number.
        // For odd-length names the rounding byte doubles as the null terminator.
        // For even-length names no null is written (n == nameLen).
        int n = (nameLen % 2 == 0) ? nameLen : nameLen + 1;

        // unk2: symbol-table slot byte offset (= slot_index * 6, first-appearance order).
        // Only assigned for plain Variables (token 0x0006) that are not proc-def (flag 0x80).
        int unk2 = 0;
        if (tokenValue == 0x0006 && (flags & 0x80) == 0) {
            unk2 = varSlots.computeIfAbsent(lowerName, k -> nextVarSlot++ * 6);
        }

        writeUint16(tokenValue, out);
        out.write(unk1 & 0xFF);
        out.write(unk2 & 0xFF);
        out.write(n);
        out.write(flags);
        out.write(nameBytes);
        if (nameLen % 2 != 0) {
            out.write(0x00); // null / alignment pad for odd-length names
        }
    }

    /**
     * Encodes a plain keyword token, appending extra zero bytes for tokens
     * that require runtime back-patch space (back-patch counts come from the token table).
     */
    private void encodeKeyword(int value, ByteArrayOutputStream out) throws IOException {
        writeUint16(value, out);
        int extra = tokenTable.extraBytesFor(value);
        for (int i = 0; i < extra; i++) out.write(0x00);
    }

    /**
     * Encodes an extension command token.
     *
     * Format: [004E:2] [slot:1] [0xFF:1] [offset:2 big-endian signed]
     * The 0xFF byte is written by AMOS as an "unresolved" marker; AMOS patches it at load time.
     */
    private void encodeExtKeyword(int slot, int offset, ByteArrayOutputStream out) throws IOException {
        writeUint16(0x004E, out);
        out.write(slot & 0xFF);
        out.write(0xFF); // unresolved marker — AMOS patches this at load time
        writeUint16(offset, out); // 2-byte big-endian (may be negative, but written as uint16)
    }

    // -------------------------------------------------------------------------
    // Conversion helpers
    // -------------------------------------------------------------------------

    /**
     * Converts a Java float to the AMOS single-precision float encoding.
     *
     * AMOS format (32 bits):
     *   bits 31-8: mantissa (24 bits), MSB is always set for non-zero values
     *   bit 7:     unused / ignored
     *   bits 6-0:  exponent (7 bits)
     *
     * value = mantissa * 2^(exponent - 88)
     *
     * Derivation from IEEE 754:
     *   IEEE 754: value = ieeeMantissa * 2^(ieeeExp - 23)
     *             where ieeeMantissa is the 24-bit integer with implicit leading 1
     *   AMOS:     value = amosMantissa * 2^(amosExp - 88)
     *   If amosMantissa == ieeeMantissa: amosExp = ieeeExp - 23 + 88 = ieeeExp + 65
     */
    static int floatToAmos(float value) {
        if (value == 0.0f) return 0;
        // Work with absolute value; AMOS format doesn't encode sign
        int bits = Float.floatToRawIntBits(Math.abs(value));
        int ieeeExp = ((bits >> 23) & 0xFF) - 127; // unbiased IEEE exponent
        int ieeeMantissa = (bits & 0x7FFFFF) | 0x800000; // 24-bit with implicit leading 1
        int amosExp = ieeeExp + 65;
        // amosExp should fit in 7 bits (0-127); clamp to valid range
        if (amosExp < 0) amosExp = 0;
        if (amosExp > 127) amosExp = 127;
        // AMOS word: mantissa in upper 24 bits, exponent in lower 8 (bits 6-0, bit 7 unused)
        return (ieeeMantissa << 8) | (amosExp & 0x7F);
    }

    // -------------------------------------------------------------------------
    // Low-level write helpers
    // -------------------------------------------------------------------------

    private static void writeUint16(int value, ByteArrayOutputStream out) {
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeInt32(int value, ByteArrayOutputStream out) {
        out.write((value >> 24) & 0xFF);
        out.write((value >> 16) & 0xFF);
        out.write((value >> 8) & 0xFF);
        out.write(value & 0xFF);
    }

    private static void writeUint64(long value, ByteArrayOutputStream out) throws IOException {
        var bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        out.write(bytes);
    }

    private static int varFlags(AmosToken.Variable v) {
        int flags = switch (v.type()) {
            case INTEGER -> 0x00;
            case FLOAT -> 0x01;
            case STRING -> 0x02;
        };
        if (v.isArray()) flags |= 0x40;
        flags |= v.extraFlags();
        return flags;
    }
}
