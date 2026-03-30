package dev.rambris.amos.tokenizer;

import dev.rambris.amos.tokenizer.model.AmosFile;
import dev.rambris.amos.tokenizer.model.AmosLine;
import dev.rambris.amos.tokenizer.model.AmosVersion;

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

    public Tokenizer() {
        this(AmosVersion.PRO_101);
    }

    public Tokenizer(AmosVersion version) {
        this.version = version;
        TokenTable tokenTable = new TokenTable();
        this.parser  = new AsciiParser(tokenTable);
        this.encoder = new BinaryEncoder();
        this.writer  = new AmosFileWriter();
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
                while (end < rest.length() && (Character.isLetterOrDigit(rest.charAt(end)) || rest.charAt(end) == '_')) end++;
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
        var amosLines = java.util.Arrays.stream(rawLines)
                .map(this::tokenizeLine)
                .toList();
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
        var lines = java.util.Arrays.stream(rawLines)
                .map(this::tokenizeLine)
                .toList();
        return new AmosFile(version, lines);
    }

    // -------------------------------------------------------------------------
    // AmosFile → binary
    // -------------------------------------------------------------------------

    /**
     * Encodes an {@link AmosFile} into a complete AMOS binary file.
     */
    public byte[] encode(AmosFile file) {
        List<byte[]> encodedLines = file.lines().stream()
                .map(line -> encoder.encodeLine(line.indent(), line.tokens()))
                .toList();
        return writer.write(file.version(), encodedLines);
    }

    // -------------------------------------------------------------------------
    // Convenience: parse + encode in one step
    // -------------------------------------------------------------------------

    /** Parses {@code asciiSource} and immediately encodes to AMOS binary. */
    public byte[] tokenizeToBytes(String asciiSource) {
        return encode(parse(asciiSource));
    }
}
