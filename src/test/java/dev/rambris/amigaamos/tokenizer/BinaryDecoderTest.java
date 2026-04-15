/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosToken;
import dev.rambris.amigaamos.tokenizer.model.AmosVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AmosFileReader} and {@link BinaryDecoder}.
 *
 * <p>Uses the reference {@code .AMOS} files in {@code src/test/resources/} to verify
 * that decoding produces the correct version, a plausible line count, and specific
 * well-known tokens.  The roundtrip test (decode → re-encode) verifies structural
 * equivalence via the line count.
 */
class BinaryDecoderTest {

    private static final Path PALETTE = Path.of("src/test/resources/PaletteEditor.AMOS");
    private static final Path NUMBERS = Path.of("src/test/resources/Numbers.AMOS");
    private static final Path PROC2 = Path.of("src/test/resources/Procedures_2.AMOS");

    // -------------------------------------------------------------------------
    // Version detection
    // -------------------------------------------------------------------------

    @Test
    void version_paletteEditor_isPro101() throws Exception {
        var file = new Tokenizer().decode(PALETTE);
        assertEquals(AmosVersion.PRO_101, file.version());
    }

    @Test
    void version_numbers_isBasic13() throws Exception {
        var file = new Tokenizer().decode(NUMBERS);
        assertEquals(AmosVersion.BASIC_13, file.version());
    }

    @Test
    void version_procedures2_isBasic134() throws Exception {
        var file = new Tokenizer().decode(PROC2);
        assertEquals(AmosVersion.BASIC_134, file.version());
    }

    // -------------------------------------------------------------------------
    // Line counts
    // -------------------------------------------------------------------------

    @Test
    void lineCount_paletteEditor_matchesAscSource() throws Exception {
        // PaletteEditor.Asc has 227 lines; trailing blank lines are stripped by the
        // tokenizer so the binary will have somewhat fewer.
        var file = new Tokenizer().decode(PALETTE);
        assertTrue(file.lines().size() > 200,
                "Expected >200 lines, got " + file.lines().size());
    }

    @Test
    void lineCount_numbers_matchesAscSource() throws Exception {
        var file = new Tokenizer().decode(NUMBERS);
        assertTrue(file.lines().size() > 60,
                "Expected >60 lines, got " + file.lines().size());
    }

    // -------------------------------------------------------------------------
    // Token types on known lines
    // -------------------------------------------------------------------------

    @Test
    void secondLine_paletteEditor_hasSingleQuoteRem() throws Exception {
        // Line 0 is a blank line; line 1 is a ' Screens comment
        var file = new Tokenizer().decode(PALETTE);
        var line1 = file.lines().get(1);
        assertFalse(line1.tokens().isEmpty(), "Line 1 should have a comment token");
        assertTrue(line1.tokens().get(0) instanceof AmosToken.SingleQuoteRem,
                "Line 1 should start with a SingleQuoteRem");
    }

    @Test
    void decodes_intLiterals_correctly() {
        // Encode a simple integer line and decode it back
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var encoded = tokenizer.tokenizeToBytes("Print 42");
        var decoded = tokenizer.decode(encoded);

        assertTrue(decoded.lines().size() >= 1);
        var tokens = decoded.lines().get(0).tokens();
        // Should contain: Print keyword, then DecimalInt(42)
        var hasFortyTwo = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.DecimalInt d && d.value() == 42);
        assertTrue(hasFortyTwo, "Should find Int(42) in decoded tokens: " + tokens);
    }

    @Test
    void decodes_stringLiteral_correctly() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var encoded = tokenizer.tokenizeToBytes("Print \"hello\"");
        var decoded = tokenizer.decode(encoded);

        var tokens = decoded.lines().get(0).tokens();
        var hasHello = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.DoubleQuoteString s && "hello".equals(s.text()));
        assertTrue(hasHello, "Should find Str(\"hello\") in decoded tokens: " + tokens);
    }

    @Test
    void decodes_rem_correctly() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var encoded = tokenizer.tokenizeToBytes("Rem This is a comment");
        var decoded = tokenizer.decode(encoded);

        var tokens = decoded.lines().get(0).tokens();
        var hasRem = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.Rem r && r.text().contains("comment"));
        assertTrue(hasRem, "Should find Rem with comment text: " + tokens);
    }

    @Test
    void decodes_variable_withCorrectType() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        // Integer variable
        var encoded = tokenizer.tokenizeToBytes("x=1");
        var decoded = tokenizer.decode(encoded);

        var tokens = decoded.lines().get(0).tokens();
        var hasX = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.Variable v
                               && "x".equals(v.name())
                               && v.type() == AmosToken.VarType.INTEGER);
        assertTrue(hasX, "Should find integer variable 'x': " + tokens);
    }

    @Test
    void decodes_floatVariable_withCorrectType() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var encoded = tokenizer.tokenizeToBytes("x#=1.5");
        var decoded = tokenizer.decode(encoded);

        var tokens = decoded.lines().get(0).tokens();
        // The # suffix is not stored in the name; it is encoded in the flags byte
        var hasX = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.Variable v
                               && "x".equals(v.name())
                               && v.type() == AmosToken.VarType.FLOAT);
        assertTrue(hasX, "Should find float variable 'x' with FLOAT type: " + tokens);
    }

    @Test
    void decodes_hexInt_correctly() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var encoded = tokenizer.tokenizeToBytes("x=$FF");
        var decoded = tokenizer.decode(encoded);

        var tokens = decoded.lines().get(0).tokens();
        var hasHex = tokens.stream()
                .anyMatch(t -> t instanceof AmosToken.HexInt h && h.value() == 0xFF);
        assertTrue(hasHex, "Should find HexInt($FF): " + tokens);
    }

    // -------------------------------------------------------------------------
    // Roundtrip: decode → re-encode produces the same line count
    // -------------------------------------------------------------------------

    @Test
    void roundtrip_paletteEditor_sameLineCount() throws Exception {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var original = Files.readAllBytes(PALETTE);
        var decoded = tokenizer.decode(original);
        var reEncoded = tokenizer.encode(decoded);

        // Line counts must match
        assertEquals(countLines(original), countLines(reEncoded),
                "Re-encoded file should have the same number of lines");
    }

    @Test
    void roundtrip_numbers_sameLineCount() throws Exception {
        var tokenizer = new Tokenizer(AmosVersion.BASIC_13);
        var original = Files.readAllBytes(NUMBERS);
        var decoded = tokenizer.decode(original);
        var reEncoded = tokenizer.encode(decoded);

        assertEquals(countLines(original), countLines(reEncoded));
    }

    // -------------------------------------------------------------------------
    // Helper: count encoded lines in a raw .AMOS byte array
    // -------------------------------------------------------------------------

    private static int countLines(byte[] data) {
        int count = 0;
        int pos = 20; // skip header + code-length field
        while (pos < data.length) {
            int wc = data[pos] & 0xFF;
            if (wc == 0) break;
            count++;
            pos += wc * 2;
        }
        return count;
    }
}
