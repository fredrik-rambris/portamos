/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosFile;
import dev.rambris.amigaamos.tokenizer.model.AmosLine;
import dev.rambris.amigaamos.tokenizer.model.AmosToken;
import dev.rambris.amigaamos.tokenizer.model.AmosToken.VarType;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * Converts an {@link AmosFile} back to a list of ASCII source lines.
 *
 * <p>This is the inverse of {@link AsciiParser}: given the in-memory token representation
 * it produces text that can be fed back to {@link AsciiParser} to reproduce the same tokens.
 *
 * <p>Spacing rules:
 * <ul>
 *   <li>Indent: {@code indent - 1} leading spaces (AMOS uses 1-based indent).</li>
 *   <li>Space before each token, except:
 *     <ul>
 *       <li>At the start of the line (after the indent).</li>
 *       <li>After an open-bracket token: {@code (} or {@code [}.</li>
 *       <li>Before a close-bracket or separator: {@code )}, {@code ]}, {@code ,}, {@code ;},
 *           {@code :}, {@code #}.</li>
 *       <li>Operator and punctuation tokens (symbols like {@code =}, {@code +}, …) are
 *           output without surrounding spaces so that {@code x=1} reads naturally.</li>
 *     </ul>
 *   </li>
 *   <li>A {@code Label} token is printed as {@code name:} — the colon is appended directly
 *       (no space), which is required for the re-parser to recognise the label definition.</li>
 *   <li>A {@code Rem} token that appears mid-line terminates the line (as the parser does).</li>
 * </ul>
 */
class AsciiPrinter {

    private final TokenTable tokenTable;

    AsciiPrinter(TokenTable tokenTable) {
        this.tokenTable = tokenTable;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Token value of the AMOSPro Compiler's compiled-body marker line.
     */
    static final int TOK_COMPILED_BODY = 0x2BF4;

    /**
     * PEM-style delimiter for compiled procedure bodies.
     */
    static final String PEM_BEGIN = "' ----- BEGIN COMPILED CODE -----";
    static final String PEM_END = "' ----- END COMPILED CODE -----";

    /**
     * Base64 characters per PEM comment line (after the leading {@code "' "}).
     */
    private static final int PEM_LINE_WIDTH = 60;

    /**
     * Converts an {@link AmosFile} to a list of ASCII source lines.
     * One element per {@link AmosLine}; no trailing newline on any element.
     *
     * <p>If the file has a compiled body, the line containing the compiled-body
     * marker token ({@code $2BF4}) is replaced by a PEM-style block of
     * {@code '} comment lines with the body base64-encoded, so that
     * {@link Tokenizer#parse(String)} can reconstruct it.
     */
    List<String> print(AmosFile file) {
        var result = new ArrayList<String>(file.lines().size());
        for (var line : file.lines()) {
            if (file.hasCompiledBody() && isCompiledBodyMarkerLine(line)) {
                result.addAll(pemBlock(file.compiledBody(), line.indent()));
            } else {
                result.add(printLine(line));
            }
        }
        return result;
    }

    /**
     * Returns {@code true} if {@code line} consists solely of the compiled-body marker token.
     */
    private static boolean isCompiledBodyMarkerLine(AmosLine line) {
        return line.tokens().size() == 1
               && line.tokens().get(0) instanceof AmosToken.Keyword kw
               && kw.value() == TOK_COMPILED_BODY;
    }

    /**
     * Emits a PEM-style block for the given compiled body bytes.
     *
     * <p>Format:
     * <pre>
     *    ' ----- BEGIN COMPILED CODE -----
     *    ' AAAA…(base64, 60 chars/line)…AAAA
     *    ' ----- END COMPILED CODE -----
     * </pre>
     * The leading spaces match the procedure body indent.
     */
    private static List<String> pemBlock(byte[] body, int indent) {
        var prefix = " ".repeat(Math.max(0, indent - 1));
        var lines = new ArrayList<String>();
        lines.add(prefix + PEM_BEGIN);
        var b64 = Base64.getEncoder().encodeToString(body);
        for (int i = 0; i < b64.length(); i += PEM_LINE_WIDTH) {
            lines.add(prefix + "' " + b64.substring(i, Math.min(i + PEM_LINE_WIDTH, b64.length())));
        }
        lines.add(prefix + PEM_END);
        return lines;
    }

    // -------------------------------------------------------------------------
    // Single line
    // -------------------------------------------------------------------------

    String printLine(AmosLine line) {
        var tokens = line.tokens();
        int indent = line.indent() > 0 ? line.indent() - 1 : 0;
        var prefix = " ".repeat(indent);

        if (tokens.isEmpty()) {
            return "";
        }

        // Whole-line comment
        if (tokens.size() == 1 && tokens.get(0) instanceof AmosToken.SingleQuoteRem rem) {
            return prefix + "'" + rem.text();
        }

        // Rem-only line (Rem keyword at line start)
        if (tokens.get(0) instanceof AmosToken.Rem rem) {
            return prefix + "Rem" + rem.text();
        }

        var sb = new StringBuilder(prefix);
        boolean first = true;
        boolean suppressNextSpace = false; // true after ( or [

        for (var token : tokens) {
            // Rem mid-line: append and stop
            if (token instanceof AmosToken.Rem rem) {
                if (!first) sb.append(' ');
                sb.append("Rem").append(rem.text());
                break;
            }

            boolean isClose = isCloseTok(token);
            boolean isOp = isOperatorTok(token);

            if (!first && !suppressNextSpace && !isClose && !isOp) {
                sb.append(' ');
            } else if (!first && !suppressNextSpace && isOp) {
                // Operators are appended without a space
            }

            sb.append(tokenText(token));
            first = false;
            suppressNextSpace = isOpenTok(token);
        }

        return sb.toString();
    }

    // -------------------------------------------------------------------------
    // Token → text
    // -------------------------------------------------------------------------

    private String tokenText(AmosToken token) {
        return switch (token) {
            case AmosToken.SingleQuoteRem rem -> "'" + rem.text();
            case AmosToken.Rem rem -> "Rem" + rem.text();
            case AmosToken.DoubleQuoteString s -> "\"" + s.text() + "\"";
            case AmosToken.SingleQuoteString s -> "'" + s.text() + "'";
            case AmosToken.DecimalInt i -> Integer.toString(i.value());
            case AmosToken.HexInt h -> "$" + Integer.toHexString(h.value()).toUpperCase();
            case AmosToken.BinaryInt b -> "%" + Integer.toBinaryString(b.value());
            case AmosToken.Flt f -> formatFloat(f.value());
            case AmosToken.Dbl d -> formatDouble(d.value());
            case AmosToken.Variable v -> v.name() + varSuffix(v.type());
            case AmosToken.Label l -> l.name() + ":";
            case AmosToken.ProcRef p -> p.name();
            case AmosToken.LabelRef l -> l.name();
            case AmosToken.Keyword k -> {
                var name = tokenTable.displayNameFor(k.value());
                yield name != null ? name : String.format("$%04X", k.value());
            }
            case AmosToken.ExtKeyword e -> {
                int key = (e.slot() << 16) | (e.offset() & 0xFFFF);
                var name = tokenTable.displayNameFor(key);
                yield name != null ? name : String.format("$%04X:%04X", e.slot(), e.offset());
            }
        };
    }

    // -------------------------------------------------------------------------
    // Spacing helpers
    // -------------------------------------------------------------------------

    /**
     * Returns true if this token is an open bracket/paren — no space after it.
     */
    private static boolean isOpenTok(AmosToken token) {
        return token instanceof AmosToken.Keyword kw
               && (kw.value() == 0x0074 || kw.value() == 0x0084); // ( or [
    }

    /**
     * Returns true if this token is a close bracket, comma, or semicolon —
     * no space before it.
     *
     * <p>Note: {@code :} (statement separator) is intentionally NOT listed here.
     * A leading space is required before {@code :} to prevent the re-parser from
     * interpreting {@code name:} as a label definition.
     */
    private static boolean isCloseTok(AmosToken token) {
        if (!(token instanceof AmosToken.Keyword kw)) return false;
        return switch (kw.value()) {
            case 0x007C, // )
                 0x008C, // ]
                 0x005C, // ,
                 0x0064, // ;
                 0x006C  // #
                    -> true;
            default -> false;
        };
    }

    /**
     * Returns true if this token is a symbolic operator — no space before or after.
     * Operators are single/double-char symbols like {@code =}, {@code +}, {@code <>}.
     *
     * <p>The {@code :} statement-separator (0x0054) is excluded even though it is in
     * {@code CORE_OPERATORS}: it must be preceded by a space so the re-parser does not
     * interpret {@code name:} mid-line as a label definition.
     */
    private boolean isOperatorTok(AmosToken token) {
        if (!(token instanceof AmosToken.Keyword kw)) return false;
        if (kw.value() == 0x0054) return false; // ':' must have space before it
        return tokenTable.isOperator(kw.value());
    }

    // -------------------------------------------------------------------------
    // Variable type suffix
    // -------------------------------------------------------------------------

    private static String varSuffix(VarType type) {
        return switch (type) {
            case FLOAT -> "#";
            case STRING -> "$";
            case INTEGER -> "";
        };
    }

    // -------------------------------------------------------------------------
    // Numeric formatters
    // -------------------------------------------------------------------------

    /**
     * Formats a Java {@code float} as an AMOS float literal.
     *
     * <p>Java uses E-notation without a sign for positive exponents ({@code "1.0E17"}).
     * AMOS source uses a space before E and an explicit sign ({@code "1.0 E+17"}).
     * Both forms are accepted by {@link AsciiParser}.
     */
    static String formatFloat(float value) {
        var s = Float.toString(value);
        return convertENotation(s);
    }

    /**
     * Formats a Java {@code double} as an AMOS double literal (with {@code #} suffix).
     */
    static String formatDouble(double value) {
        var s = Double.toString(value);
        return convertENotation(s) + "#";
    }

    /**
     * Converts Java E-notation ({@code "1.0E17"}, {@code "1.0E-4"}) to AMOS E-notation
     * ({@code "1.0 E+17"}, {@code "1.0 E-4"}) by inserting a space and ensuring an explicit sign.
     * Returns the string unchanged if no exponent is present.
     */
    private static String convertENotation(String s) {
        int eIdx = s.indexOf('E');
        if (eIdx < 0) eIdx = s.indexOf('e');
        if (eIdx < 0) return s;

        var mantissa = s.substring(0, eIdx);
        var expPart = s.substring(eIdx + 1); // e.g. "17" or "-4" or "+17"
        if (!expPart.startsWith("+") && !expPart.startsWith("-")) {
            expPart = "+" + expPart;
        }
        return mantissa + " E" + expPart;
    }
}
