package dev.rambris.amos.tokenizer;

import dev.rambris.amos.tokenizer.model.AmosToken;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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

    /**
     * Encodes a single source line into AMOS binary format.
     *
     * @param indent the indent level (0 = top level)
     * @param tokens the tokens on this line (not including the implicit EOL)
     * @return the complete binary representation of the line
     */
    byte[] encodeLine(int indent, List<AmosToken> tokens) {
        ByteArrayOutputStream body = new ByteArrayOutputStream();

        for (AmosToken token : tokens) {
            try {
                encodeToken(token, body);
            } catch (IOException e) {
                throw new RuntimeException("Failed to encode token: " + token, e);
            }
        }

        // EOL marker: 0x0000
        byte[] bodyBytes = body.toByteArray();

        // Total = 2 (header) + bodyBytes.length + 2 (EOL)
        int totalBytes = 2 + bodyBytes.length + 2;
        // Must be even (should always be since we pad odd-length fields)
        if (totalBytes % 2 != 0) {
            throw new IllegalStateException("Line byte count is odd: " + totalBytes);
        }
        int totalWords = totalBytes / 2;

        ByteArrayOutputStream line = new ByteArrayOutputStream(totalBytes);
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
            case AmosToken.Variable v -> encodeNamedToken(0x0006, v.name(), varFlags(v.type()), out);
            case AmosToken.Label l -> encodeNamedToken(0x000C, l.name(), 0x00, out);
            case AmosToken.ProcRef p -> encodeNamedToken(0x0012, p.name(), 0x00, out);
            case AmosToken.LabelRef l -> encodeNamedToken(0x0018, l.name(), 0x00, out);
            case AmosToken.Keyword k -> writeUint16(k.value(), out);
            case AmosToken.ExtKeyword e -> encodeExtKeyword(e.slot(), e.offset(), out);
        }
    }

    /**
     * Encodes a REM-style token (SingleQuoteRem or Rem keyword).
     *
     * Format: [token:2] [unused:00] [len:1] [text:n] [pad if n odd:00]
     *
     * The EOL's first byte (0x00) acts as null terminator for the C-string.
     */
    private void encodeRem(int tokenValue, String text, ByteArrayOutputStream out) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.US_ASCII);
        int n = textBytes.length;
        writeUint16(tokenValue, out);
        out.write(0x00); // unused byte
        out.write(n);    // length (number of chars, not including null)
        out.write(textBytes);
        if (n % 2 != 0) {
            out.write(0x00); // padding to make even
        }
        // EOL (0x0000) is written by encodeLine; its first 0x00 serves as null terminator
    }

    /**
     * Encodes a quoted string token (double-quote or single-quote string).
     *
     * Format: [token:2] [len_hi:1] [len_lo:1] [text:n] [pad if n odd:00]
     */
    private void encodeQuotedString(int tokenValue, String text, ByteArrayOutputStream out) throws IOException {
        byte[] textBytes = text.getBytes(StandardCharsets.US_ASCII);
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
        long bits = Double.doubleToRawLongBits(value);
        writeUint64(bits, out);
    }

    /**
     * Encodes a named token: Variable, Label, ProcRef, or LabelRef.
     *
     * Format: [token:2] [unknown:00 00] [len:1] [flags:1] [name+null:n] [pad if n odd:00]
     *
     * n = strlen(name) + 1 (includes null terminator in the byte count)
     * Name is stored in lowercase.
     */
    private void encodeNamedToken(int tokenValue, String name, int flags, ByteArrayOutputStream out)
            throws IOException {
        String lowerName = name.toLowerCase();
        byte[] nameBytes = lowerName.getBytes(StandardCharsets.US_ASCII);
        int n = nameBytes.length + 1; // +1 for null terminator
        writeUint16(tokenValue, out);
        out.write(0x00); // unknown
        out.write(0x00); // unknown
        out.write(n);    // length including null
        out.write(flags);
        out.write(nameBytes);
        out.write(0x00); // null terminator
        if (n % 2 != 0) {
            out.write(0x00); // padding to make n bytes even
        }
    }

    /**
     * Encodes an extension command token.
     *
     * Format: [004E:2] [slot:1] [unused:00] [offset:2 big-endian signed]
     */
    private void encodeExtKeyword(int slot, int offset, ByteArrayOutputStream out) throws IOException {
        writeUint16(0x004E, out);
        out.write(slot & 0xFF);
        out.write(0x00); // unused
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
        byte[] bytes = new byte[8];
        for (int i = 7; i >= 0; i--) {
            bytes[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        out.write(bytes);
    }

    private static int varFlags(AmosToken.VarType type) {
        return switch (type) {
            case INTEGER -> 0x00;
            case FLOAT -> 0x01;
            case STRING -> 0x02;
        };
    }
}
