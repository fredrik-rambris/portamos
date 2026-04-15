/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosFile;
import dev.rambris.amigaamos.tokenizer.model.AmosLine;
import dev.rambris.amigaamos.tokenizer.model.AmosToken;
import dev.rambris.amigaamos.tokenizer.model.AmosVersion;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Converts between ASCII AMOS source and the in-memory {@link AmosFile} representation,
 * and between {@link AmosFile} and the AMOS binary file format.
 *
 * Typical parse-then-encode pipeline:
 * <pre>
 *   Tokenizer tokenizer = new Tokenizer(AmosVersion.PRO_101);
 *   AmosFile  amosFile  = tokenizer.parse(path, StandardCharsets.ISO_8859_1);
 *   byte[]    binary    = tokenizer.encode(amosFile);
 * </pre>
 */
public class Tokenizer {

    /**
     * Each leading ASCII space increases the indent level by one.
     * AMOS uses 1-based indent; level 1 means no indentation (0 leading spaces).
     * Formula: indent = leadingSpaces + 1
     */
    static final int AMOS_INDENT_SPACES = 1;

    private final AmosVersion version;
    private final TokenTable tokenTable;
    private final AsciiParser parser;
    private final BinaryEncoder encoder;
    private final AmosFileWriter writer;
    private final AsciiPrinter printer;

    /**
     * When {@code true}, all {@code Procedure} blocks are marked folded in the AMOS editor.
     */
    private boolean foldProcedures = false;

    public Tokenizer() {
        this(AmosVersion.PRO_101);
    }

    public Tokenizer(AmosVersion version) {
        this.version = version;
        this.tokenTable = new TokenTable();
        this.parser = new AsciiParser(tokenTable);
        this.encoder = new BinaryEncoder(tokenTable);
        this.writer = new AmosFileWriter();
        this.printer = new AsciiPrinter(tokenTable);
    }

    /**
     * Loads an additional extension definition JSON file into the token table.
     * Must be called before {@link #parse}.
     *
     * @param path path to a definition JSON file (same format as the built-in ones)
     * @return {@code this} for chaining
     */
    public Tokenizer withDefinition(Path path) {
        tokenTable.loadFile(path);
        return this;
    }

    /**
     * Enables default folding of all {@code Procedure} blocks.
     *
     * <p>When set, every {@code Procedure} without a magic-comment marker will
     * have its fold flag (bit 7) set, causing the AMOS editor to display it
     * collapsed.  This does not affect procedures that use {@code !!LOCKED!!}
     * or {@code !!ENCRYPTED!!} — those always set their own combination of flags.
     *
     * @return {@code this} for chaining
     */
    public Tokenizer withFoldedProcedures() {
        this.foldProcedures = true;
        return this;
    }

    // -------------------------------------------------------------------------
    // Pre-scan: collect procedure names declared in the source
    // -------------------------------------------------------------------------

    /**
     * Scans raw source lines for {@code Procedure NAME[...]} declarations and
     * returns the set of declared procedure names (lowercase).
     */
    private static Set<String> scanProcedureNames(String[] lines) {
        var names = new HashSet<String>();
        for (var line : lines) {
            var stripped = line.stripLeading();
            // Match "Procedure NAME" or "Procedure NAME[...]"
            if (stripped.regionMatches(true, 0, "Procedure ", 0, 10)) {
                var rest = stripped.substring(10).stripLeading();
                var end = 0;
                while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) end++;
                if (end > 0) names.add(rest.substring(0, end).toLowerCase());
            }
        }
        return names;
    }

    /**
     * Scans raw source lines for {@code Dim NAME(...)} declarations and
     * returns the set of array variable names (lowercase).
     */
    private static Set<String> scanArrayVarNames(String[] lines) {
        var names = new HashSet<String>();
        for (var line : lines) {
            var stripped = line.stripLeading();
            // Match "Dim NAME(" - the variable immediately following the Dim keyword
            if (stripped.regionMatches(true, 0, "Dim ", 0, 4)) {
                var rest = stripped.substring(4).stripLeading();
                var end = 0;
                while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end))
                        || rest.charAt(end) == '_' || rest.charAt(end) == '$' || rest.charAt(end) == '#')) end++;
                if (end > 0) names.add(rest.substring(0, end).toLowerCase());
            }
        }
        return names;
    }

    // -------------------------------------------------------------------------
    // ASCII → AmosFile
    // -------------------------------------------------------------------------

    /**
     * Parses one ASCII source line into an {@link AmosLine}.
     *
     * The indent level is derived from leading spaces:
     *   {@code indent = leadingSpaces / AMOS_INDENT_SPACES + 1}
     * (AMOS uses 1-based indent; level 1 means no indentation.)
     */
    AmosLine tokenizeLine(String line) {
        int spaces = 0;
        while (spaces < line.length() && line.charAt(spaces) == ' ') spaces++;
        int indent = spaces + 1;
        return new AmosLine(indent, parser.parseLine(line));
    }

    /**
     * Parses an AMOS ASCII source file into an {@link AmosFile}.
     *
     * Trailing blank lines are stripped so they do not produce spurious
     * empty lines in the binary output.
     */
    public AmosFile parse(Path path, Charset charset) throws IOException {
        var lines = Files.lines(path, charset)
                .collect(Collectors.toCollection(ArrayList::new));
        while (!lines.isEmpty() && lines.getLast().isBlank()) lines.removeLast();
        var rawLines = lines.toArray(String[]::new);
        parser.setProcedureNames(scanProcedureNames(rawLines));
        parser.setArrayVarNames(scanArrayVarNames(rawLines));
        var result = tokenizeLinesWithPem(rawLines);
        return new AmosFile(version, result.lines(), List.of(), result.compiledBody());
    }

    /** Convenience overload that uses ISO-8859-1 (typical AMOS charset). */
    public AmosFile parse(Path path) throws IOException {
        return parse(path, StandardCharsets.ISO_8859_1);
    }

    /**
     * Parses an AMOS ASCII source string into an {@link AmosFile}.
     * Trailing blank lines are stripped.
     *
     * <p>PEM-style compiled-body blocks (emitted by {@link AsciiPrinter}) are
     * recognised and decoded back to raw bytes stored in {@link AmosFile#compiledBody()}.
     */
    public AmosFile parse(String asciiSource) {
        var rawLines = asciiSource.split("\n", -1);
        int lineCount = rawLines.length;
        while (lineCount > 0 && rawLines[lineCount - 1].isBlank()) lineCount--;
        rawLines = java.util.Arrays.copyOf(rawLines, lineCount);
        parser.setProcedureNames(scanProcedureNames(rawLines));
        parser.setArrayVarNames(scanArrayVarNames(rawLines));
        var result = tokenizeLinesWithPem(rawLines);
        return new AmosFile(version, result.lines(), List.of(), result.compiledBody());
    }

    // -------------------------------------------------------------------------
    // binary → AmosFile  (detokenizer)
    // -------------------------------------------------------------------------

    /**
     * Decodes a {@code .AMOS} binary file into an {@link AmosFile}.
     *
     * <p>Any {@code --definition} files loaded via {@link #withDefinition} are used
     * during decoding so extension keywords are correctly parsed.
     *
     * @param path path to the {@code .AMOS} file
     * @return the decoded program (banks are not decoded — use the {@code export} command)
     */
    public AmosFile decode(Path path) throws IOException {
        return new AmosFileReader(tokenTable).read(path);
    }

    /**
     * Decodes raw {@code .AMOS} binary bytes into an {@link AmosFile}.
     *
     * @param data raw bytes of a {@code .AMOS} file
     * @return the decoded program
     */
    public AmosFile decode(byte[] data) {
        return new AmosFileReader(tokenTable).read(data);
    }

    // -------------------------------------------------------------------------
    // AmosFile → ASCII
    // -------------------------------------------------------------------------

    /**
     * Converts an {@link AmosFile} to a list of ASCII source lines.
     *
     * <p>The returned list has one element per line; no trailing newline.
     * Feed the result back to {@link #parse(String)} to round-trip the program.
     */
    public List<String> print(AmosFile file) {
        return printer.print(file);
    }

    /**
     * Converts an {@link AmosFile} to an ASCII source string (lines joined by {@code \n}).
     */
    public String printToString(AmosFile file) {
        return String.join("\n", printer.print(file));
    }

    /**
     * Writes an {@link AmosFile} as an ASCII source file.
     *
     * @param file    the decoded program
     * @param path    output path for the {@code .Asc} file
     * @param charset charset to use (typically ISO-8859-1 for AMOS)
     */
    public void print(AmosFile file, Path path, Charset charset) throws IOException {
        var lines = printer.print(file);
        Files.writeString(path,
                String.join("\n", lines) + "\n",
                charset);
    }

    /**
     * Convenience overload that uses ISO-8859-1.
     */
    public void print(AmosFile file, Path path) throws IOException {
        print(file, path, StandardCharsets.ISO_8859_1);
    }

    // -------------------------------------------------------------------------
    // AmosFile → binary
    // -------------------------------------------------------------------------

    /**
     * Encodes an {@link AmosFile} into a complete AMOS binary file.
     */
    public byte[] encode(AmosFile file) {
        var lines = file.lines();
        var encodedLines = new ArrayList<byte[]>(lines.size());
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            try {
                encodedLines.add(encoder.encodeLine(line.indent(), line.tokens()));
            } catch (TokenizeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new TokenizeException(i + 1, -1, null, e);
            }
        }
        return writer.write(file.version(), encodedLines, file.banks(), foldProcedures,
                file.compiledBody());
    }

    // -------------------------------------------------------------------------
    // Internal: tokenize a pre-scanned array of raw source lines
    // -------------------------------------------------------------------------

    /**
     * Holds both the tokenized lines and any compiled body decoded from PEM blocks.
     */
    private record ParseResult(List<AmosLine> lines, byte[] compiledBody) {
    }

    /**
     * Tokenizes a pre-scanned line array, recognising PEM-style compiled-body
     * blocks and converting them back to a compiled-body marker line plus raw bytes.
     *
     * <p>A PEM block spans multiple source lines:
     * <pre>
     *   (optional indent)' ----- BEGIN COMPILED CODE -----
     *   (optional indent)' &lt;base64 chunk&gt;
     *   ...
     *   (optional indent)' ----- END COMPILED CODE -----
     * </pre>
     * The block is replaced by a single {@link AmosLine} containing
     * {@code Keyword(0x2BF4)} (the compiled-body marker), at the indent level
     * inferred from the leading spaces on the BEGIN line.
     */
    private ParseResult tokenizeLinesWithPem(String[] rawLines) {
        var result = new ArrayList<AmosLine>(rawLines.length);
        byte[] compiledBody = null;
        int i = 0;
        while (i < rawLines.length) {
            var stripped = rawLines[i].stripLeading();
            if (stripped.startsWith(AsciiPrinter.PEM_BEGIN.stripLeading())) {
                // Count indent from leading spaces
                int spaces = rawLines[i].length() - stripped.length();
                int indent = spaces + 1;

                // Collect base64 chunks until PEM_END
                var b64 = new StringBuilder();
                i++;
                while (i < rawLines.length) {
                    var s = rawLines[i].strip();
                    if (s.equals(AsciiPrinter.PEM_END.strip())) {
                        i++;
                        break;
                    }
                    // Strip leading "' " from comment line
                    if (s.startsWith("' ")) b64.append(s.substring(2));
                    else if (s.startsWith("'")) b64.append(s.substring(1));
                    i++;
                }
                compiledBody = Base64.getDecoder().decode(b64.toString());

                // Emit the compiled-body marker line
                result.add(new AmosLine(indent,
                        List.of(new AmosToken.Keyword(AsciiPrinter.TOK_COMPILED_BODY))));
            } else {
                try {
                    result.add(tokenizeLine(rawLines[i]));
                } catch (TokenizeException e) {
                    throw e;
                } catch (RuntimeException e) {
                    throw new TokenizeException(i + 1, -1, rawLines[i], e);
                }
                i++;
            }
        }
        return new ParseResult(result, compiledBody);
    }

    /**
     * Legacy helper kept for internal callers that do not need PEM handling.
     */
    private List<AmosLine> tokenizeLines(String[] rawLines) {
        return tokenizeLinesWithPem(rawLines).lines();
    }

    // -------------------------------------------------------------------------
    // Convenience: parse + encode in one step
    // -------------------------------------------------------------------------

    /** Parses {@code asciiSource} and immediately encodes to AMOS binary. */
    public byte[] tokenizeToBytes(String asciiSource) {
        return encode(parse(asciiSource));
    }
}
