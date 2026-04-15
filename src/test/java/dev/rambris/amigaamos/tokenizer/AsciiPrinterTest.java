/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosVersion;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link AsciiPrinter} via the {@link Tokenizer} print API.
 *
 * <p>Two categories of tests:
 * <ol>
 *   <li>Unit tests: tokenize a short snippet, print it, and verify the output string.</li>
 *   <li>Roundtrip tests: decode a reference {@code .AMOS} binary, print to ASCII,
 *       re-parse, re-encode, and verify that the re-encoded binary has the same
 *       line count and per-line token count as the original.</li>
 * </ol>
 */
class AsciiPrinterTest {

    private static final Path PALETTE = Path.of("src/test/resources/PaletteEditor.AMOS");
    private static final Path NUMBERS = Path.of("src/test/resources/Numbers.AMOS");
    private static final Path PROC2 = Path.of("src/test/resources/Procedures_2.AMOS");
    private static final Path COMPILED = Path.of("src/test/resources/Compiled.AMOS");

    // -------------------------------------------------------------------------
    // Float formatting
    // -------------------------------------------------------------------------

    @Test
    void formatFloat_noExponent_unchanged() {
        assertEquals("100000.0", AsciiPrinter.formatFloat(100000.0f));
        assertEquals("0.001", AsciiPrinter.formatFloat(0.001f));
    }

    @Test
    void formatFloat_positiveExponent_addsSpaceAndSign() {
        var result = AsciiPrinter.formatFloat(1e17f);
        // Must contain " E+" to be recognized by AsciiParser
        assertTrue(result.contains(" E+"), "Expected AMOS E-notation, got: " + result);
    }

    @Test
    void formatFloat_negativeExponent_addsSpace() {
        var result = AsciiPrinter.formatFloat(1e-4f);
        assertTrue(result.contains(" E-"), "Expected AMOS E-notation for negative exponent, got: " + result);
    }

    @Test
    void formatDouble_appendsHash() {
        var result = AsciiPrinter.formatDouble(1.5);
        assertTrue(result.endsWith("#"), "Double literal should end with #: " + result);
    }

    // -------------------------------------------------------------------------
    // Print → re-parse roundtrip for specific snippets
    // -------------------------------------------------------------------------

    /**
     * Verifies that printing a decoded snippet re-parses to the same token binary.
     */
    private void assertRoundtrip(String source) {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var original = tokenizer.tokenizeToBytes(source);
        var decoded = tokenizer.decode(original);
        var printed = tokenizer.printToString(decoded);
        var reparsed = tokenizer.tokenizeToBytes(printed);
        assertArrayEquals(original, reparsed,
                "Roundtrip failed for: " + source + "\nPrinted as: " + printed);
    }

    @Test
    void roundtrip_intLiteral() {
        assertRoundtrip("Print 42");
    }

    @Test
    void roundtrip_hexLiteral() {
        assertRoundtrip("x=$FF");
    }

    @Test
    void roundtrip_stringLiteral() {
        assertRoundtrip("Print \"hello\"");
    }

    @Test
    void roundtrip_singleQuoteRem() {
        assertRoundtrip("' This is a comment");
    }

    @Test
    void roundtrip_remKeyword() {
        assertRoundtrip("Rem a comment line");
    }

    @Test
    void roundtrip_intVariable() {
        assertRoundtrip("x=1");
    }

    @Test
    void roundtrip_floatVariable() {
        assertRoundtrip("x#=1.5");
    }

    @Test
    void roundtrip_stringVariable() {
        assertRoundtrip("A$=\"hello\"");
    }

    @Test
    void roundtrip_labelAndGoto() {
        // Use a name that is not an AMOS keyword so it is encoded as a Label token
        assertRoundtrip("Goto ERRLABEL\nERRLABEL:\nPrint 1");
    }

    @Test
    void roundtrip_procedureCall() {
        assertRoundtrip("Procedure FOO\nEnd Proc\nFOO");
    }

    @Test
    void roundtrip_procedureWithArgs() {
        assertRoundtrip("Procedure BAR[x]\nEnd Proc\nBAR[1]");
    }

    @Test
    void roundtrip_forLoop() {
        assertRoundtrip("For i=1 To 10\nNext i");
    }

    @Test
    void roundtrip_ifElse() {
        assertRoundtrip("If x=1\nPrint \"yes\"\nEnd If");
    }

    @Test
    void roundtrip_arithmetic() {
        assertRoundtrip("y=x+1");
    }

    @Test
    void roundtrip_nestedParens() {
        assertRoundtrip("x=Abs(-5)");
    }

    @Test
    void roundtrip_colonSeparator() {
        assertRoundtrip("x=1 : y=2");
    }

    @Test
    void roundtrip_floatLiteral() {
        // Flt token
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var original = tokenizer.tokenizeToBytes("x#=3.14");
        var decoded = tokenizer.decode(original);
        var printed = tokenizer.printToString(decoded);
        var reparsed = tokenizer.tokenizeToBytes(printed);
        assertArrayEquals(original, reparsed,
                "Float literal roundtrip failed; printed as: " + printed);
    }

    // -------------------------------------------------------------------------
    // Roundtrip against reference .AMOS files
    // -------------------------------------------------------------------------

    /**
     * Decodes a reference binary, prints it to ASCII, re-parses, re-encodes, and
     * compares per-line token counts with the original decoded file.
     */
    private static void assertFileRoundtrip(Path path, AmosVersion version) throws Exception {
        var tokenizer = new Tokenizer(version);
        var original = tokenizer.decode(path);
        var printed = tokenizer.printToString(original);
        var reparsed = tokenizer.parse(printed);
        var reencoded = tokenizer.encode(reparsed);
        var reDecoded = tokenizer.decode(reencoded);

        assertEquals(original.lines().size(), reDecoded.lines().size(),
                "Line count mismatch after roundtrip for " + path);

        for (int i = 0; i < original.lines().size(); i++) {
            var origToks = original.lines().get(i).tokens();
            var reToks = reDecoded.lines().get(i).tokens();
            assertEquals(origToks.size(), reToks.size(),
                    "Token count mismatch on line " + i + " for " + path);
        }
    }

    @Test
    void fileRoundtrip_paletteEditor() throws Exception {
        assertFileRoundtrip(PALETTE, AmosVersion.PRO_101);
    }

    @Test
    void fileRoundtrip_numbers() throws Exception {
        assertFileRoundtrip(NUMBERS, AmosVersion.BASIC_13);
    }

    @Test
    void fileRoundtrip_procedures2() throws Exception {
        assertFileRoundtrip(PROC2, AmosVersion.BASIC_134);
    }

    /**
     * Compiled.AMOS contains a procedure compiled by AMOSPro Compiler.
     * The compiled body (sentinel + m68k code) must survive the print→reparse cycle.
     * We verify:
     * 1. The printed ASCII contains a PEM BEGIN/END block.
     * 2. Line count and per-line token counts are preserved (structural equivalence).
     * 3. The compiled body bytes are preserved byte-for-byte.
     * <p>
     * Note: the overall binary is not byte-identical because named-token unk2 slot-offset
     * bytes (a known gap) differ — the file format test only checks structural equivalence.
     */
    @Test
    void fileRoundtrip_compiled_structuralAndBody() throws Exception {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var original = tokenizer.decode(COMPILED);
        var printed = tokenizer.printToString(original);

        // PEM block must be present
        assertTrue(printed.contains(AsciiPrinter.PEM_BEGIN),
                "Printed ASCII should contain PEM BEGIN marker");
        assertTrue(printed.contains(AsciiPrinter.PEM_END),
                "Printed ASCII should contain PEM END marker");

        var reparsed = tokenizer.parse(printed);

        // Structural equivalence: same number of lines, same token counts per line
        assertEquals(original.lines().size(), reparsed.lines().size(),
                "Line count mismatch after compiled roundtrip");
        for (int i = 0; i < original.lines().size(); i++) {
            assertEquals(original.lines().get(i).tokens().size(),
                    reparsed.lines().get(i).tokens().size(),
                    "Token count mismatch on line " + i);
        }

        // Compiled body must be byte-for-byte identical
        assertTrue(original.hasCompiledBody(), "Original should have a compiled body");
        assertTrue(reparsed.hasCompiledBody(), "Reparsed file should have a compiled body");
        assertArrayEquals(original.compiledBody(), reparsed.compiledBody(),
                "Compiled body must be preserved byte-for-byte through print→reparse");
    }

    // -------------------------------------------------------------------------
    // Specific output format checks
    // -------------------------------------------------------------------------

    @Test
    void print_label_hasColonDirectlyAttached() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        // "ERRLABEL" is not an AMOS keyword so it encodes as a Label token (not Keyword + ':')
        var decoded = tokenizer.decode(tokenizer.tokenizeToBytes("ERRLABEL:\nGoto ERRLABEL"));
        var lines = tokenizer.print(decoded);
        // The label line should be "errlabel:" — colon directly appended by tokenText(Label)
        var labelLine = lines.stream()
                .filter(l -> l.contains(":") && !l.startsWith("Goto"))
                .findFirst().orElse("");
        assertFalse(labelLine.contains(" :"),
                "Label colon must not be preceded by a space: \"" + labelLine + "\"");
        assertTrue(labelLine.contains("errlabel:"),
                "Expected 'errlabel:' in label line: \"" + labelLine + "\"");
    }

    @Test
    void print_singleQuoteRem_hasLeadingQuote() {
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var decoded = tokenizer.decode(tokenizer.tokenizeToBytes("' My comment"));
        var lines = tokenizer.print(decoded);
        assertEquals("' My comment", lines.get(0));
    }

    @Test
    void print_indent_matchesLevel() {
        // A For loop body has indent level 2 (1 extra space)
        var tokenizer = new Tokenizer(AmosVersion.PRO_101);
        var decoded = tokenizer.decode(tokenizer.tokenizeToBytes("For i=1 To 3\n Print i\nNext i"));
        var lines = tokenizer.print(decoded);
        // The Print line should have exactly 1 leading space (indent 2 → 1 space)
        assertTrue(lines.get(1).startsWith(" "), "Inner loop line should be indented: \"" + lines.get(1) + "\"");
    }
}
