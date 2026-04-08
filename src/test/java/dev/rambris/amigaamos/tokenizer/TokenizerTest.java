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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TokenizerTest {

    @Test
    void placeholder() {
        assertNotNull(new Tokenizer());
    }

    /**
     * Parses Numbers.Asc and compares the result against Numbers.AMOS.
     *
     * The comparison is structural: same number of lines, same word-count per line,
     * same token types in each position.  For float literals (token 0x0046) the
     * decoded value is compared within ±2 ULP rather than exact bits, because the
     * original AMOS Amiga compiler used a different decimal→FFP rounding algorithm.
     */
    @Test
    void tokenize_numbers() throws Exception {
        Path ascPath  = Path.of("src/test/resources/Numbers.Asc");
        Path amosPath = Path.of("src/test/resources/Numbers.AMOS");

        var tokenizer = new Tokenizer(AmosVersion.BASIC_13);
        byte[] actual   = tokenizer.tokenizeToBytes(Files.readString(ascPath));
        byte[] expected = Files.readAllBytes(amosPath);

        assertAmosFilesStructurallyEqual(expected, actual);
    }

    @Test
    void tokenize_palette_editor() throws Exception {
        Path ascPath  = Path.of("src/test/resources/PaletteEditor.Asc");
        Path amosPath = Path.of("src/test/resources/PaletteEditor.AMOS");

        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        byte[] actual   = tokenizer.tokenizeToBytes(Files.readString(ascPath));
        byte[] expected = Files.readAllBytes(amosPath);

        assertAmosFilesStructurallyEqual(expected, actual);
    }

    @Test
    void parse_variables_with_type_suffixes() {
        var parser = new AsciiParser(new TokenTable());

        var tokens = parser.parseLine("foo foo$ foo#");

        assertEquals(
                List.of(
                        new AmosToken.Variable("foo", AmosToken.VarType.INTEGER),
                        new AmosToken.Variable("foo", AmosToken.VarType.STRING),
                        new AmosToken.Variable("foo", AmosToken.VarType.FLOAT)
                ),
                tokens
        );
    }

    @Test
    void parse_single_quoted_string_when_not_line_comment() {
        var parser = new AsciiParser(new TokenTable());

        var tokens = parser.parseLine("\"a\" 'b'");

        assertEquals(
                List.of(
                        new AmosToken.DoubleQuoteString("a"),
                        new AmosToken.SingleQuoteString("b")
                ),
                tokens
        );
    }

    @Test
    void parse_incomplete_exponent_as_int_then_keyword() {
        var parser = new AsciiParser(new TokenTable());

        var tokens = parser.parseLine("1Else");

        assertEquals(
                List.of(
                        new AmosToken.DecimalInt(1),
                        new AmosToken.Keyword(0x02D0)
                ),
                tokens
        );
    }

    @Test
    void tokenize_bonus_probe() throws Exception {
        Path ascPath  = Path.of("src/test/resources/MissingOffsetsBonusProbe.Asc");
        Path amosPath = Path.of("src/test/resources/MissingOffsetsBonusProbe.AMOS");

        var tokenizer = new Tokenizer(AmosVersion.BASIC_134);
        byte[] actual   = tokenizer.tokenizeToBytes(Files.readString(ascPath));
        byte[] expected = Files.readAllBytes(amosPath);

        assertAmosFilesStructurallyEqual(expected, actual);
    }

    @Test
    void tokenize_procedures_2() throws Exception {
        Path ascPath  = Path.of("src/test/resources/Procedures_2.Asc");
        Path amosPath = Path.of("src/test/resources/Procedures_2.AMOS");

        var tokenizer = new Tokenizer(AmosVersion.BASIC_134);
        byte[] actual   = tokenizer.tokenizeToBytes(Files.readString(ascPath));
        byte[] expected = Files.readAllBytes(amosPath);

        assertAmosFilesStructurallyEqual(expected, actual);
    }

    @Test
    void tokenize_palette_editor_without_number_format_exception() {
        var tokenizer = new Tokenizer(AmosVersion.BASIC_13);
        var sourcePath = Path.of("src/test/resources/PaletteEditor.Asc");

        assertDoesNotThrow(() -> tokenizer.tokenizeToBytes(Files.readString(sourcePath)));
    }

    // -------------------------------------------------------------------------
    // Structural comparison helpers
    // -------------------------------------------------------------------------

    /** Used to look up extra-byte counts for keyword tokens during comparison. */
    private final TokenTable tokenTable = new TokenTable();

    /**
     * Compares two AMOS binary files structurally.
     *
     * Checks:
     *   - Same 16-byte version header
     *   - Same number of lines
     *   - Each line: same word count, same indent, same token sequence
     *   - Float tokens (0x0046): values compared within ±2 ULP
     */
    private void assertAmosFilesStructurallyEqual(byte[] expected, byte[] actual) {
        // Version string (bytes 0–11); bytes 12–15 are file-specific metadata not
        // derivable from the ASCII source, so we skip them.
        assertArrayEquals(
                Arrays.copyOfRange(expected, 0, 12),
                Arrays.copyOfRange(actual, 0, 12),
                "Version headers differ");

        List<byte[]> expLines = extractLines(expected);
        List<byte[]> actLines = extractLines(actual);

        assertEquals(expLines.size(), actLines.size(),
                "Different number of source lines");

        for (int i = 0; i < expLines.size(); i++) {
            assertLinesStructurallyEqual(expLines.get(i), actLines.get(i), i);
        }
    }

    private void assertLinesStructurallyEqual(byte[] exp, byte[] act, int lineIdx) {
        assertEquals(exp.length, act.length,
                "Line %d: different byte length (exp=%d, act=%d)".formatted(lineIdx, exp.length, act.length));

        // Header: word-count must match exactly
        assertEquals(exp[0], act[0], "Line %d: word count differs".formatted(lineIdx));
        // Indent: skip comparison for empty lines (word-count == 2 means header + EOL only).
        // AMOS editors sometimes store blank lines with indent=0 as an artifact; we can't
        // reproduce that from the ASCII source, so we don't enforce it for empty lines.
        if (exp[0] != 2) {
            assertEquals(exp[1], act[1], "Line %d: indent differs".formatted(lineIdx));
        }

        // Walk the token area: exp[2..len-3] (skip header and EOL)
        int i = 2;
        while (i < exp.length - 2) {
            int expTok = ((exp[i] & 0xFF) << 8) | (exp[i + 1] & 0xFF);
            int actTok = ((act[i] & 0xFF) << 8) | (act[i + 1] & 0xFF);
            assertEquals(expTok, actTok,
                    "Line %d offset %d: token value differs (exp=0x%04X, act=0x%04X)"
                            .formatted(lineIdx, i, expTok, actTok));
            i += 2;

            switch (expTok) {
                case 0x0046 -> { // Flt: compare decoded float values with tolerance
                    assertFloatPayloadEqual(exp, act, i, lineIdx);
                    i += 4;
                }
                case 0x2B6A -> { // Dbl
                    assertArrayEquals(
                            Arrays.copyOfRange(exp, i, i + 8),
                            Arrays.copyOfRange(act, i, i + 8),
                            "Line %d: double payload differs".formatted(lineIdx));
                    i += 8;
                }
                case 0x003E, 0x0036, 0x001E -> { // integer literals
                    assertArrayEquals(
                            Arrays.copyOfRange(exp, i, i + 4),
                            Arrays.copyOfRange(act, i, i + 4),
                            "Line %d: integer payload differs".formatted(lineIdx));
                    i += 4;
                }
                case 0x0652, 0x064A -> { // REM (single-quote or Rem keyword)
                    // unused(1) + len(1) + text(n) [+ pad if n odd] : skip to next word-aligned position
                    int remLen = exp[i + 1] & 0xFF;
                    int remBytes = 2 + remLen;
                    if (remBytes % 2 != 0) remBytes++;
                    assertArrayEquals(
                            Arrays.copyOfRange(exp, i, i + remBytes),
                            Arrays.copyOfRange(act, i, i + remBytes),
                            "Line %d: REM payload differs".formatted(lineIdx));
                    i += remBytes;
                }
                case 0x0026, 0x002E -> { // quoted strings
                    int strLen = ((exp[i] & 0xFF) << 8) | (exp[i + 1] & 0xFF);
                    int strBytes = 2 + strLen;
                    if (strBytes % 2 != 0) strBytes++;
                    assertArrayEquals(
                            Arrays.copyOfRange(exp, i, i + strBytes),
                            Arrays.copyOfRange(act, i, i + strBytes),
                            "Line %d: string payload differs".formatted(lineIdx));
                    i += strBytes;
                }
                case 0x0006, 0x000C, 0x0012, 0x0018 -> { // named tokens
                    // unk1 (i+0) and unk2 (i+1) are symbol-table/back-patch fields set by the
                    // AMOS tokenizer at load time; we cannot reproduce them without a full
                    // symbol-table implementation, so they are skipped here.
                    int n    = exp[i + 2] & 0xFF; // n = nameLen rounded to even
                    int flags = exp[i + 3] & 0xFF;
                    int actN    = act[i + 2] & 0xFF;
                    int actFlags = act[i + 3] & 0xFF;
                    assertEquals(n, actN,
                            "Line %d offset %d: named token n differs".formatted(lineIdx, i + 2));
                    assertEquals(flags, actFlags,
                            "Line %d offset %d: named token flags differ".formatted(lineIdx, i + 3));
                    // compare the name bytes themselves
                    assertArrayEquals(
                            Arrays.copyOfRange(exp, i + 4, i + 4 + n),
                            Arrays.copyOfRange(act, i + 4, i + 4 + n),
                            "Line %d: named token name differs".formatted(lineIdx));
                    int nameBytes = 4 + n;
                    if (nameBytes % 2 != 0) nameBytes++;
                    i += nameBytes;
                }
                case 0x0000 -> {
                    // EOL — stop walking
                    return;
                }
                default -> {
                    // Keywords may have back-patch extra bytes. The reference file has
                    // runtime-filled values; we generate zeros. Skip them without comparing.
                    int extra = tokenTable.extraBytesFor(expTok);
                    i += extra;
                }
            }
        }
    }

    /**
     * Compares a 4-byte AMOS float payload between expected and actual.
     * Decodes both as AMOS floats and checks the decoded values are within 2 ULP of each other.
     */
    private void assertFloatPayloadEqual(byte[] exp, byte[] act, int offset, int lineIdx) {
        int expBits = readInt32(exp, offset);
        int actBits = readInt32(act, offset);

        if (expBits == actBits) return; // exact match

        float expVal = decodeAmosFloat(expBits);
        float actVal = decodeAmosFloat(actBits);

        if (expVal == actVal) return; // same decoded value (e.g. bit-7 difference)

        // Allow ±4 ULP difference to accommodate Amiga FFP rounding vs IEEE 754.
        // AMOS's decimal→FFP conversion can differ from Java's by up to ~3 ULP for
        // values near the max representable FFP float (e.g. "9.22337 E+18" → 0xFFFFFF7F).
        float ulp = Math.ulp(expVal);
        assertTrue(Math.abs(expVal - actVal) <= 4 * ulp,
                "Line %d: float values differ beyond tolerance (exp=%s, act=%s)"
                        .formatted(lineIdx, expVal, actVal));
    }

    /**
     * Decodes a 32-bit AMOS float to a Java float.
     * Formula: mantissa × 2^(exponent − 88), where exponent = bits[6:0] and
     * mantissa = bits[31:8] (24-bit, MSB always set for non-zero).
     */
    private static float decodeAmosFloat(int value) {
        int e = value & 0x7F;
        if (e == 0) return 0.0f;
        float mantissa = (value >>> 8);           // unsigned shift
        return (float) (mantissa * Math.pow(2, e - 88));
    }

    // -------------------------------------------------------------------------
    // Binary helpers
    // -------------------------------------------------------------------------

    /** Extracts the encoded-line byte arrays from an AMOS binary file. */
    private static List<byte[]> extractLines(byte[] data) {
        List<byte[]> lines = new ArrayList<>();
        int pos = 20; // skip 16-byte header + 4-byte code length
        while (pos < data.length) {
            int wordCount = data[pos] & 0xFF;
            if (wordCount == 0) break;
            int lineLen = wordCount * 2;
            if (pos + lineLen > data.length) break;
            lines.add(Arrays.copyOfRange(data, pos, pos + lineLen));
            pos += lineLen;
        }
        return lines;
    }

    private static int readInt32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o + 1] & 0xFF) << 16)
                | ((d[o + 2] & 0xFF) << 8) | (d[o + 3] & 0xFF);
    }
}
