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
    private final Set<String> skipDefinitionIds = new LinkedHashSet<>();
    private final List<Path> extraDefinitionPaths = new ArrayList<>();

    // Lazily initialised on first use so withoutDefinition() can be called
    // after construction but before any parse/encode/print operation.
    private TokenTable tokenTable;
    private AsciiParser parser;
    private BinaryEncoder encoder;
    private final AmosFileWriter writer = new AmosFileWriter();
    private AsciiPrinter printer;

    /**
     * When {@code true}, all {@code Procedure} blocks are marked folded in the AMOS editor.
     */
    private boolean foldProcedures = false;

    public Tokenizer() {
        this(AmosVersion.PRO_101);
    }

    public Tokenizer(AmosVersion version) {
        this.version = version;
    }

    private TokenTable tokenTable() {
        if (tokenTable == null) {
            tokenTable = new TokenTable(skipDefinitionIds);
            for (var p : extraDefinitionPaths) tokenTable.loadFile(p);
            parser = new AsciiParser(tokenTable);
            encoder = new BinaryEncoder(tokenTable);
            printer = new AsciiPrinter(tokenTable);
        }
        return tokenTable;
    }

    /**
     * Loads an additional extension definition JSON file into the token table.
     * Must be called before any parse/encode/print operation.
     *
     * @param path path to a definition JSON file (same format as the built-in ones)
     * @return {@code this} for chaining
     */
    public Tokenizer withDefinition(Path path) {
        if (tokenTable != null) throw new IllegalStateException(
                "withDefinition() must be called before parse/encode/print");
        extraDefinitionPaths.add(path);
        return this;
    }

    /**
     * Prevents a built-in extension definition from being loaded into the token table.
     * Use this when the target program replaces a built-in extension with a different one
     * (e.g. AMCAF replaces the Music extension at runtime).
     *
     * <p>The {@code id} is matched case-insensitively against the {@code extension.id}
     * field in each built-in JSON file.  Known built-in IDs:
     * {@code Core}, {@code Music}, {@code Compact}, {@code Request}, {@code IOPorts},
     * {@code Compiler}.  The same check also applies to files added via
     * {@link #withDefinition(Path)}.
     *
     * <p>Must be called before any parse/encode/print operation.
     *
     * @param id the extension ID to skip (case-insensitive)
     * @return {@code this} for chaining
     */
    public Tokenizer withoutDefinition(String id) {
        if (tokenTable != null) throw new IllegalStateException(
                "withoutDefinition() must be called before parse/encode/print");
        skipDefinitionIds.add(id.toUpperCase());
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
        tokenTable(); // ensure init
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
        tokenTable(); // ensure init
        parser.setProcedureNames(scanProcedureNames(rawLines));
        parser.setArrayVarNames(scanArrayVarNames(rawLines));
        var result = tokenizeLinesWithPem(rawLines);
        return new AmosFile(version, result.lines(), List.of(), result.compiledBody());
    }

    /** Convenience overload that uses UTF-8. */
    public AmosFile parse(Path path) throws IOException {
        return parse(path, StandardCharsets.UTF_8);
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
        tokenTable(); // ensure init
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
        return new AmosFileReader(tokenTable()).read(path);
    }

    /**
     * Decodes raw {@code .AMOS} binary bytes into an {@link AmosFile}.
     *
     * @param data raw bytes of a {@code .AMOS} file
     * @return the decoded program
     */
    public AmosFile decode(byte[] data) {
        return new AmosFileReader(tokenTable()).read(data);
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
        tokenTable(); // ensure init
        return printer.print(file);
    }

    /**
     * Converts an {@link AmosFile} to an ASCII source string (lines joined by {@code \n}).
     */
    public String printToString(AmosFile file) {
        tokenTable(); // ensure init
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
        tokenTable(); // ensure init
        var lines = printer.print(file);
        Files.writeString(path,
                String.join("\n", lines) + "\n",
                charset);
    }

    /**
     * Convenience overload that uses UTF-8.
     */
    public void print(AmosFile file, Path path) throws IOException {
        print(file, path, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // AmosFile → binary
    // -------------------------------------------------------------------------

    /**
     * Encodes an {@link AmosFile} into a complete AMOS binary file.
     */
    public byte[] encode(AmosFile file) {
        tokenTable(); // ensure init
        var lines = file.lines();
        var encodedLines = new ArrayList<byte[]>(lines.size());
        // Maps encoded-line index → key3 value (symbol table byte size = varCount * 6)
        // for each Procedure header line.
        var procKey3 = new HashMap<Integer, Integer>();
        int currentProcIdx = -1;

        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            try {
                encodedLines.add(encoder.encodeLine(line.indent(), line.tokens()));
            } catch (TokenizeException e) {
                throw e;
            } catch (RuntimeException e) {
                throw new TokenizeException(i + 1, -1, null, e);
            }
            // After encoding: if EndProc, record the symbol-table size for the proc header.
            // The scope reset happens on the NEXT Procedure keyword, so scopeVarTableSize()
            // still reflects the just-finished procedure here.
            for (var tok : line.tokens()) {
                if (tok instanceof AmosToken.Keyword k) {
                    if (k.value() == 0x0390 && currentProcIdx >= 0) { // End Proc
                        procKey3.put(currentProcIdx, encoder.scopeVarTableSize());
                        currentProcIdx = -1;
                    } else if (k.value() == 0x0376) { // Procedure (scope already reset by encoder)
                        currentProcIdx = i;
                    }
                }
            }
        }
        patchControlFlow(lines, encodedLines);
        return writer.write(file.version(), encodedLines, file.banks(), foldProcedures,
                file.compiledBody(), procKey3);
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

    // -------------------------------------------------------------------------
    // Control-flow back-patch post-processing
    // -------------------------------------------------------------------------

    private static final int TOK_CF_FOR     = 0x023C;
    private static final int TOK_CF_NEXT    = 0x0246;
    private static final int TOK_CF_REPEAT  = 0x0250;
    private static final int TOK_CF_UNTIL   = 0x025C;
    private static final int TOK_CF_WHILE   = 0x0268;
    private static final int TOK_CF_WEND    = 0x0274;
    private static final int TOK_CF_DO      = 0x027E;
    private static final int TOK_CF_LOOP    = 0x0286;
    private static final int TOK_CF_EXIT_IF = 0x0290;
    private static final int TOK_CF_EXIT    = 0x029E;
    private static final int TOK_CF_IF      = 0x02BE;
    private static final int TOK_CF_THEN    = 0x02C6;
    private static final int TOK_CF_ELSE    = 0x02D0;
    private static final int TOK_CF_END_IF  = 0x02DA;
    private static final int TOK_CF_ELSE_IF = 0x25A4;
    private static final int TOK_ON         = 0x0316;

    /**
     * Computes and patches the 2-byte control-flow back-patch field in each block-start line.
     *
     * <p>The back-patch field is at bytes 4–5 of the line (right after the 2-byte header and
     * the 2-byte CF token). Its value is the number of bytes from that field to the byte
     * immediately following the matching end-line:
     * <pre>  (startLine.length - 6) + body_size + endLine.length + 2</pre>
     *
     * <p>Inline If (same line contains Then): {@code back_patch = lineSize - 8}.
     *
     * <p>Else acts as both a block-end (closes the preceding If) and a block-start (opens
     * the Else branch); its own back-patch is patched when End If is found.
     */
    private void patchControlFlow(List<AmosLine> lines, List<byte[]> encodedLines) {
        var stack     = new ArrayDeque<int[]>(); // [tokenValue, lineIdx] — all block starts
        var loopStack = new ArrayDeque<int[]>(); // [tokenValue, lineIdx] — loop starts only

        // Deferred Exit/Exit If patches: [exitLineIdx, loopStartIdx]
        var pendingExits = new ArrayList<int[]>();
        // loopStartIdx → loopEndLineIdx, filled when Next/Until/Wend/Loop is found
        var loopEndMap = new HashMap<Integer, Integer>();

        for (int i = 0; i < lines.size(); i++) {
            var line    = lines.get(i);
            var encoded = encodedLines.get(i);

            boolean hasFor = false, hasNext = false;
            boolean hasRepeat = false, hasUntil = false;
            boolean hasWhile = false, hasWend = false;
            boolean hasDo = false, hasLoop = false;
            boolean hasIf = false, hasThen = false;
            boolean hasElse = false, hasElseIf = false, hasEndIf = false;
            boolean hasExitIf = false, hasExit = false;

            // Byte offset of the On token within the encoded line (-1 = not present on this line)
            int onByteOffset = -1;
            // Count of branch targets (ProcRef or LabelRef) for the On back-patch
            int onTargetCount = 0;
            // Running byte offset through the encoded line (header = 2 bytes)
            int byteOffset = 2;

            for (var tok : line.tokens()) {
                if (tok instanceof AmosToken.Keyword k) {
                    switch (k.value()) {
                        case TOK_CF_FOR     -> hasFor     = true;
                        case TOK_CF_NEXT    -> hasNext    = true;
                        case TOK_CF_REPEAT  -> hasRepeat  = true;
                        case TOK_CF_UNTIL   -> hasUntil   = true;
                        case TOK_CF_WHILE   -> hasWhile   = true;
                        case TOK_CF_WEND    -> hasWend    = true;
                        case TOK_CF_DO      -> hasDo      = true;
                        case TOK_CF_LOOP    -> hasLoop    = true;
                        case TOK_CF_EXIT_IF -> hasExitIf  = true;
                        case TOK_CF_EXIT    -> hasExit    = true;
                        case TOK_CF_IF      -> hasIf      = true;
                        case TOK_CF_THEN    -> hasThen    = true;
                        case TOK_CF_ELSE    -> hasElse    = true;
                        case TOK_CF_ELSE_IF -> hasElseIf  = true;
                        case TOK_CF_END_IF  -> hasEndIf   = true;
                        case TOK_ON         -> onByteOffset = byteOffset;
                    }
                }
                if (tok instanceof AmosToken.ProcRef || tok instanceof AmosToken.LabelRef) onTargetCount++;
                byteOffset += tokenByteSize(tok);
            }

            // On back-patch: bytes[0-1] = argument-list byte count, bytes[2-3] = branch-target count.
            // The argument area is everything after On's 6-byte block (2-byte token + 4 extra bytes)
            // up to but not including the 2-byte EOL: lineSize - onByteOffset - 8.
            if (onByteOffset >= 0) {
                int bp0 = encoded.length - onByteOffset - 8;
                encoded[onByteOffset + 2] = (byte) ((bp0 >> 8) & 0xFF);
                encoded[onByteOffset + 3] = (byte) (bp0 & 0xFF);
                encoded[onByteOffset + 4] = (byte) ((onTargetCount >> 8) & 0xFF);
                encoded[onByteOffset + 5] = (byte) (onTargetCount & 0xFF);
            }

            // Push loop block-start tokens onto both stacks
            if (hasFor)    { stack.push(new int[]{TOK_CF_FOR,    i}); loopStack.push(new int[]{TOK_CF_FOR,    i}); }
            if (hasRepeat) { stack.push(new int[]{TOK_CF_REPEAT, i}); loopStack.push(new int[]{TOK_CF_REPEAT, i}); }
            if (hasWhile)  { stack.push(new int[]{TOK_CF_WHILE,  i}); loopStack.push(new int[]{TOK_CF_WHILE,  i}); }
            if (hasDo)     { stack.push(new int[]{TOK_CF_DO,     i}); loopStack.push(new int[]{TOK_CF_DO,     i}); }

            if (hasIf) {
                if (hasThen) {
                    // Inline If: back_patch = lineSize - 8
                    int bp = encoded.length - 8;
                    encoded[4] = (byte) ((bp >> 8) & 0xFF);
                    encoded[5] = (byte) (bp & 0xFF);
                } else {
                    stack.push(new int[]{TOK_CF_IF, i});
                }
            }

            // Else If / Else: close the preceding If/Else If block with the CHAINING formula,
            // then open a new branch block.
            if (hasElseIf) {
                if (!stack.isEmpty()) patchCfChain(stack.pop()[1], i, encodedLines);
                stack.push(new int[]{TOK_CF_ELSE_IF, i});
            } else if (hasElse) {
                if (!stack.isEmpty()) patchCfChain(stack.pop()[1], i, encodedLines);
                stack.push(new int[]{TOK_CF_ELSE, i});
            }

            // Exit If / Exit: defer patching until the enclosing loop's end is known
            if ((hasExitIf || hasExit) && !loopStack.isEmpty()) {
                pendingExits.add(new int[]{i, loopStack.peek()[1]});
            }

            // Block-end tokens: patch start with standard formula; record loop end for deferred exits
            if (hasNext  && !stack.isEmpty()) { int si = stack.pop()[1]; loopStack.pop(); patchCfStart(si, i, encodedLines); loopEndMap.put(si, i); }
            if (hasUntil && !stack.isEmpty()) { int si = stack.pop()[1]; loopStack.pop(); patchCfStart(si, i, encodedLines); loopEndMap.put(si, i); }
            if (hasWend  && !stack.isEmpty()) { int si = stack.pop()[1]; loopStack.pop(); patchCfStart(si, i, encodedLines); loopEndMap.put(si, i); }
            if (hasLoop  && !stack.isEmpty()) { int si = stack.pop()[1]; loopStack.pop(); patchCfStart(si, i, encodedLines); loopEndMap.put(si, i); }
            if (hasEndIf && !stack.isEmpty()) patchCfStart(stack.pop()[1], i, encodedLines);
        }

        // Resolve deferred Exit If / Exit patches now that all loop ends are known.
        // bytes[0-1]: (ExitLen - 6) + body_between + loopEndLen
        //             (same as standard formula minus 2 — lands at Loop/Next/Until/Wend EOL)
        // bytes[2-3]: word-count of the Exit line (ExitLen / 2)
        for (var e : pendingExits) {
            int exitIdx      = e[0];
            int loopStartIdx = e[1];
            if (!loopEndMap.containsKey(loopStartIdx)) continue; // malformed — no matching end
            int loopEndIdx = loopEndMap.get(loopStartIdx);
            var exitLine   = encodedLines.get(exitIdx);
            var loopEndLine = encodedLines.get(loopEndIdx);

            int bodySize = 0;
            for (int j = exitIdx + 1; j < loopEndIdx; j++) bodySize += encodedLines.get(j).length;
            int bp0 = (exitLine.length - 6) + bodySize + loopEndLine.length;
            int bp1 = exitLine.length / 2; // word-count

            exitLine[4] = (byte) ((bp0 >> 8) & 0xFF);
            exitLine[5] = (byte) (bp0 & 0xFF);
            exitLine[6] = (byte) ((bp1 >> 8) & 0xFF);
            exitLine[7] = (byte) (bp1 & 0xFF);
        }
    }

    /**
     * Returns the encoded byte size of {@code token}, mirroring {@link BinaryEncoder}'s encoding
     * rules.  Used to compute the byte offset of the {@code On} token within its encoded line.
     */
    private int tokenByteSize(AmosToken token) {
        return switch (token) {
            case AmosToken.Rem r            -> { int n = r.text().length();  yield 2 + 2 + n + (n % 2 != 0 ? 1 : 0); }
            case AmosToken.SingleQuoteRem r -> { int n = r.text().length();  yield 2 + 2 + n + (n % 2 != 0 ? 1 : 0); }
            case AmosToken.DoubleQuoteString s -> { int n = s.text().length(); yield 2 + 2 + n + (n % 2 != 0 ? 1 : 0); }
            case AmosToken.SingleQuoteString s -> { int n = s.text().length(); yield 2 + 2 + n + (n % 2 != 0 ? 1 : 0); }
            case AmosToken.DecimalInt ignored -> 2 + 4;
            case AmosToken.HexInt     ignored -> 2 + 4;
            case AmosToken.BinaryInt  ignored -> 2 + 4;
            case AmosToken.Flt        ignored -> 2 + 4;
            case AmosToken.Dbl        ignored -> 2 + 8;
            case AmosToken.Variable v  -> { int n = nameN(v.name());  yield 2 + 4 + n; }
            case AmosToken.Label l     -> { int n = nameN(l.name());  yield 2 + 4 + n; }
            case AmosToken.ProcRef p   -> { int n = nameN(p.name());  yield 2 + 4 + n; }
            case AmosToken.LabelRef l  -> { int n = nameN(l.name());  yield 2 + 4 + n; }
            case AmosToken.Keyword k   -> 2 + tokenTable.extraBytesFor(k.value());
            case AmosToken.ExtKeyword ignored -> 2 + 4;
        };
    }

    /** Returns name length rounded up to the nearest even number (the {@code n} field in named tokens). */
    private static int nameN(String name) {
        int len = name.length();
        return (len % 2 == 0) ? len : len + 1;
    }

    /**
     * Standard back-patch: writes (startLine.length − 6) + body + endLine.length + 2 at bytes 4–5
     * of the start line.  This formula points from the start's BP field to the byte immediately
     * after the end line — used for all loop blocks and for Else/End-If closing End-If.
     */
    private static void patchCfStart(int startIdx, int endIdx, List<byte[]> encodedLines) {
        int bodySize = 0;
        for (int j = startIdx + 1; j < endIdx; j++) bodySize += encodedLines.get(j).length;
        int bp = (encodedLines.get(startIdx).length - 6) + bodySize
                + encodedLines.get(endIdx).length + 2;
        encodedLines.get(startIdx)[4] = (byte) ((bp >> 8) & 0xFF);
        encodedLines.get(startIdx)[5] = (byte) (bp & 0xFF);
    }

    /**
     * Chaining back-patch: writes startLine.length + body at bytes 4–5 of the start line.
     * This formula points from the start's BP field to the next branch's BP field — used
     * when an If (or Else-If) block is closed by Else or Else-If rather than End-If.
     * At runtime AMOS lands at the next branch's BP field and reads that value to chain-jump.
     */
    private static void patchCfChain(int startIdx, int endIdx, List<byte[]> encodedLines) {
        int bp = encodedLines.get(startIdx).length;
        for (int j = startIdx + 1; j < endIdx; j++) bp += encodedLines.get(j).length;
        encodedLines.get(startIdx)[4] = (byte) ((bp >> 8) & 0xFF);
        encodedLines.get(startIdx)[5] = (byte) (bp & 0xFF);
    }
}
