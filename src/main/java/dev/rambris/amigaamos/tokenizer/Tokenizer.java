/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosFile;
import dev.rambris.amigaamos.tokenizer.model.AmosLine;
import dev.rambris.amigaamos.tokenizer.model.AmosVersion;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
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
    private final AsciiParser parser;
    private final BinaryEncoder encoder;
    private final AmosFileWriter writer;

    /**
     * When {@code true}, all {@code Procedure} blocks are marked folded in the
     * AMOS editor by default (bit 7 of the procedure flags byte).
     * Procedures with a {@code !!LOCKED!!} or {@code !!ENCRYPTED!!} magic comment
     * are always folded regardless of this setting.
     */
    private boolean foldProcedures = false;

    public Tokenizer() {
        this(AmosVersion.PRO_101);
    }

    public Tokenizer(AmosVersion version) {
        this.version = version;
        var tokenTable = new TokenTable();
        this.parser  = new AsciiParser(tokenTable);
        this.encoder = new BinaryEncoder(tokenTable);
        this.writer  = new AmosFileWriter();
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
        var amosLines = tokenizeLines(rawLines);
        return new AmosFile(version, amosLines);
    }

    /** Convenience overload that uses ISO-8859-1 (typical AMOS charset). */
    public AmosFile parse(Path path) throws IOException {
        return parse(path, StandardCharsets.ISO_8859_1);
    }

    /**
     * Parses an AMOS ASCII source string into an {@link AmosFile}.
     * Trailing blank lines are stripped.
     */
    public AmosFile parse(String asciiSource) {
        var rawLines = asciiSource.split("\n", -1);
        int lineCount = rawLines.length;
        while (lineCount > 0 && rawLines[lineCount - 1].isBlank()) lineCount--;
        rawLines = java.util.Arrays.copyOf(rawLines, lineCount);
        parser.setProcedureNames(scanProcedureNames(rawLines));
        parser.setArrayVarNames(scanArrayVarNames(rawLines));
        return new AmosFile(version, tokenizeLines(rawLines));
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
        return writer.write(file.version(), encodedLines, file.banks(), foldProcedures);
    }

    // -------------------------------------------------------------------------
    // Internal: tokenize a pre-scanned array of raw source lines
    // -------------------------------------------------------------------------

    private List<AmosLine> tokenizeLines(String[] rawLines) {
        var result = new ArrayList<AmosLine>(rawLines.length);
        for (int i = 0; i < rawLines.length; i++) {
            try {
                result.add(tokenizeLine(rawLines[i]));
            } catch (TokenizeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new TokenizeException(i + 1, -1, rawLines[i], e);
            }
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Convenience: parse + encode in one step
    // -------------------------------------------------------------------------

    /** Parses {@code asciiSource} and immediately encodes to AMOS binary. */
    public byte[] tokenizeToBytes(String asciiSource) {
        return encode(parse(asciiSource));
    }
}
