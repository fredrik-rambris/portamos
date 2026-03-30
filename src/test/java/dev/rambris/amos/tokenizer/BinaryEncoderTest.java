package dev.rambris.amos.tokenizer;

import org.junit.jupiter.api.Test;

import dev.rambris.amos.tokenizer.model.AmosToken;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;

class BinaryEncoderTest {

    private final BinaryEncoder encoder = new BinaryEncoder();

    private static byte[] hex(String hex) {
        String[] parts = hex.trim().split("\\s+");
        byte[] result = new byte[parts.length];
        for (int i = 0; i < parts.length; i++) {
            result[i] = (byte) Integer.parseInt(parts[i], 16);
        }
        return result;
    }

    @Test
    void emptyLine() {
        // Just EOL, indent=1: 2 bytes header + 2 bytes EOL = 4 bytes = 2 words
        assertArrayEquals(
                hex("02 01 00 00"),
                encoder.encodeLine(1, List.of())
        );
    }

    @Test
    void singleQuoteRem_evenLength() {
        // ' Screens - from PaletteEditor.AMOS (n=8, even, no pad)
        // Header(2) + token(2) + unused(1) + len(1) + text(8) + EOL(2) = 16 = 8 words
        assertArrayEquals(
                hex("08 01 06 52 00 08 20 53 63 72 65 65 6E 73 00 00"),
                encoder.encodeLine(1, List.of(new AmosToken.SingleQuoteRem(" Screens")))
        );
    }

    @Test
    void singleQuoteRem_oddLength() {
        // " xy" = 3 chars → needs 1 pad byte
        // Header(2) + token(2) + unused(1) + len(1) + text(3) + pad(1) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 06 52 00 03 20 78 79 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.SingleQuoteRem(" xy")))
        );
    }

    @Test
    void remKeyword_evenLength() {
        // Rem " hello" = 6 chars, even
        // Header(2) + token(2) + unused(1) + len(1) + text(6) + EOL(2) = 14 = 7 words
        assertArrayEquals(
                hex("07 00 06 4A 00 06 20 68 65 6C 6C 6F 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Rem(" hello")))
        );
    }

    @Test
    void decimalInt() {
        // Header(2) + token(2) + value(4) + EOL(2) = 10 = 5 words
        assertArrayEquals(
                hex("05 00 00 3E 00 00 00 2A 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.DecimalInt(42)))
        );
    }

    @Test
    void hexInt() {
        assertArrayEquals(
                hex("05 00 00 36 00 00 00 FF 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.HexInt(0xFF)))
        );
    }

    @Test
    void binaryInt() {
        assertArrayEquals(
                hex("05 00 00 1E 00 00 00 05 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.BinaryInt(0b101)))
        );
    }

    @Test
    void doubleQuoteString_odd() {
        // "hello" = 5 chars, odd → 1 pad byte
        // Header(2) + token(2) + len(2) + text(5) + pad(1) + EOL(2) = 14 = 7 words
        assertArrayEquals(
                hex("07 00 00 26 00 05 68 65 6C 6C 6F 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.DoubleQuoteString("hello")))
        );
    }

    @Test
    void doubleQuoteString_even() {
        // "hi" = 2 chars, even, no pad
        // Header(2) + token(2) + len(2) + text(2) + EOL(2) = 10 = 5 words
        assertArrayEquals(
                hex("05 00 00 26 00 02 68 69 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.DoubleQuoteString("hi")))
        );
    }

    @Test
    void singleQuoteString() {
        // "hi" = 2 chars, even, no pad; token $002E
        assertArrayEquals(
                hex("05 00 00 2E 00 02 68 69 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.SingleQuoteString("hi")))
        );
    }

    @Test
    void floatZero() {
        // Header(2) + token(2) + value(4) + EOL(2) = 10 = 5 words
        assertArrayEquals(
                hex("05 00 00 46 00 00 00 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Flt(0.0f)))
        );
    }

    @Test
    void floatMax() {
        // AMOS bytes FF FF FF 7F = mantissa 0xFFFFFF, exp 127 → ~9.223371e+18
        // The Java float with those exact IEEE bits is 0x5EFFFFFF
        // Numbers.Asc says "9.22337 E+18" but the binary stores 0xFFFFFF7F
        float amosMaxFloat = Float.intBitsToFloat(0x5EFFFFFF); // = 9.223372e18f
        assertArrayEquals(
                hex("05 00 00 46 FF FF FF 7F 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Flt(amosMaxFloat)))
        );
    }

    @Test
    void floatOne() {
        // 1.0f → mantissa=0x800000, amosExp=65(0x41) → bytes 80 00 00 41
        assertArrayEquals(
                hex("05 00 00 46 80 00 00 41 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Flt(1.0f)))
        );
    }

    @Test
    void doubleZero() {
        // Header(2) + token(2) + value(8) + EOL(2) = 14 = 7 words
        assertArrayEquals(
                hex("07 00 2B 6A 00 00 00 00 00 00 00 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Dbl(0.0)))
        );
    }

    @Test
    void variable_integer() {
        // "debug" as integer, n=strlen("debug")+1=6, flags=0x00
        // Header(2) + token(2) + unknown(2) + len(1) + flags(1) + name+null(6) + EOL(2) = 16 = 8 words
        assertArrayEquals(
                hex("08 00 00 06 00 00 06 00 64 65 62 75 67 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Variable("debug", AmosToken.VarType.INTEGER)))
        );
    }

    @Test
    void variable_integer_uppercase() {
        // "DEBUG" should be stored as lowercase "debug"
        assertArrayEquals(
                hex("08 00 00 06 00 00 06 00 64 65 62 75 67 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Variable("DEBUG", AmosToken.VarType.INTEGER)))
        );
    }

    @Test
    void variable_string() {
        // "d" as string ($), flags=0x02, n=strlen("d")+1=2, even
        // Header(2) + token(2) + unknown(2) + len(1) + flags(1) + name+null(2) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 00 06 00 00 02 02 64 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Variable("d", AmosToken.VarType.STRING)))
        );
    }

    @Test
    void variable_float() {
        // "x" as float (#), flags=0x01, n=strlen("x")+1=2, even
        // Header(2) + token(2) + unknown(2) + len(1) + flags(1) + name+null(2) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 00 06 00 00 02 01 78 00 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Variable("x", AmosToken.VarType.FLOAT)))
        );
    }

    @Test
    void variable_odd_name() {
        // "ab" has strlen=2 (even) → n=2, write "ab" (2 bytes), no null
        // Header(2) + token(2) + unknown(2) + n(1) + flags(1) + name(2) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 00 06 00 00 02 00 61 62 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Variable("ab", AmosToken.VarType.INTEGER)))
        );
    }

    @Test
    void keyword() {
        // Plain 2-byte keyword token: Header(2) + token(2) + EOL(2) = 6 = 3 words
        assertArrayEquals(
                hex("03 00 02 52 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Keyword(0x0252)))
        );
    }

    @Test
    void multipleTokens() {
        // Two keywords in one line
        // Header(2) + token1(2) + token2(2) + EOL(2) = 8 = 4 words
        assertArrayEquals(
                hex("04 00 02 52 03 00 00 00"),
                encoder.encodeLine(0, List.of(
                        new AmosToken.Keyword(0x0252),
                        new AmosToken.Keyword(0x0300)
                ))
        );
    }

    @Test
    void extKeyword() {
        // $004E [slot:1 byte] [unused:00] [offset:2 bytes big-endian]
        // Header(2) + token(2) + slot(1) + unused(1) + offset(2) + EOL(2) = 10 = 5 words
        assertArrayEquals(
                hex("05 00 00 4E 03 00 00 0A 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.ExtKeyword(3, 10)))
        );
    }

    @Test
    void label() {
        // Label "loop": strlen=4 (even) → n=4, write "loop" (4 bytes), no null
        // Header(2) + token(2) + unknown(2) + n(1) + flags(1) + name(4) + EOL(2) = 14 = 7 words
        assertArrayEquals(
                hex("07 00 00 0C 00 00 04 00 6C 6F 6F 70 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.Label("loop")))
        );
    }

    @Test
    void procRef() {
        // ProcRef "hi": strlen=2 (even) → n=2, flags=0x80, write "hi" (2 bytes), no null
        // Header(2) + token(2) + unknown(2) + n(1) + flags(1) + name(2) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 00 12 00 00 02 80 68 69 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.ProcRef("hi")))
        );
    }

    @Test
    void labelRef() {
        // LabelRef "go": strlen=2 (even) → n=2, flags=0x00, write "go" (2 bytes), no null
        // Header(2) + token(2) + unknown(2) + n(1) + flags(1) + name(2) + EOL(2) = 12 = 6 words
        assertArrayEquals(
                hex("06 00 00 18 00 00 02 00 67 6F 00 00"),
                encoder.encodeLine(0, List.of(new AmosToken.LabelRef("go")))
        );
    }
}
