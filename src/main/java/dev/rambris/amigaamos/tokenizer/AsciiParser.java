package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosToken;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parses a single ASCII AMOS source line into a sequence of AmosTokens.
 *
 * Token recognition order:
 *   1. Leading ' → SingleQuoteRem (rest of line is comment text)
 *   2. "Rem " → Rem keyword (rest of line is comment text)
 *   3. Numeric literals (decimal, hex $, binary %)
 *   4. Operator symbols and single-char punctuation
 *   5. Keywords from the token table (maximal munch: longest match wins)
 *   6. Double-quoted strings
 *   7. Single-quoted strings (after keyword scan fails)
 *   8. Variable / label references
 *
 * In AMOS, negative numbers are always encoded as a unary minus operator token
 * followed by a positive literal.  This parser therefore never emits negative
 * integer or float literals.
 */
class AsciiParser {

    private final TokenTable tokenTable;
    /** Procedure names declared via "Procedure NAME[...]" in the source. */
    private Set<String> procedureNames = Collections.emptySet();
    /** Names of array variables declared via Dim. */
    private Set<String> arrayVarNames = Collections.emptySet();
    /** Set to true after a Goto/Gosub/On…Goto keyword so the next identifier becomes a LabelRef. */
    private boolean nextIdentIsLabelRef = false;
    /** Set to true after a Dim keyword so the next variable token gets the array flag. */
    private boolean nextVarIsArray = false;
    /** Set to true after a Procedure keyword so the next identifier is a procedure-definition Variable (flags=0x80). */
    private boolean nextIdentIsProcDef = false;

    AsciiParser(TokenTable tokenTable) {
        this.tokenTable = tokenTable;
    }

    /** Called by Tokenizer before parsing to supply the known procedure names (lowercase). */
    void setProcedureNames(Set<String> names) {
        this.procedureNames = names;
    }

    /** Called by Tokenizer before parsing to supply the array variable names (lowercase). */
    void setArrayVarNames(Set<String> names) {
        this.arrayVarNames = names;
    }

    /**
     * Parses the given source line and returns its tokens.
     * The returned list does NOT include an EOL token; that is added by BinaryEncoder.
     *
     * @param line one line of AMOS ASCII source (no trailing newline expected)
     * @return the list of tokens, possibly empty for a blank line
     */
    List<AmosToken> parseLine(String line) {
        String text = line.stripLeading();

        if (text.isEmpty()) return List.of();

        // Whole-line comment: single-quote REM
        if (text.charAt(0) == '\'') {
            return List.of(new AmosToken.SingleQuoteRem(text.substring(1)));
        }

        // Rem keyword: "Rem " (case-insensitive, followed by a space)
        if (text.regionMatches(true, 0, "Rem ", 0, 4)) {
            return List.of(new AmosToken.Rem(text.substring(3)));
        }

        return parseTokens(text);
    }

    // -------------------------------------------------------------------------
    // Token stream parser
    // -------------------------------------------------------------------------

    private List<AmosToken> parseTokens(String text) {
        List<AmosToken> tokens = new ArrayList<>();
        // Reset per-line state
        nextIdentIsLabelRef = false;
        nextVarIsArray = false;
        nextIdentIsProcDef = false;
        int pos = 0;
        int len = text.length();

        while (pos < len) {
            // Skip whitespace
            while (pos < len && text.charAt(pos) == ' ') pos++;
            if (pos >= len) break;

            char c = text.charAt(pos);

            // Double-quoted string literal
            if (c == '"') {
                int end = text.indexOf('"', pos + 1);
                if (end < 0) end = len;
                tokens.add(new AmosToken.DoubleQuoteString(text.substring(pos + 1, end)));
                pos = end + 1;
                continue;
            }

            // Single-quoted string literal (non-leading quote)
            if (c == '\'') {
                int end = text.indexOf('\'', pos + 1);
                if (end < 0) end = len;
                tokens.add(new AmosToken.SingleQuoteString(text.substring(pos + 1, end)));
                pos = end + 1;
                continue;
            }

            // Inline Rem comment: consume the rest of the line as a Rem token.
            // Matches "Rem " followed by text, or a bare "Rem" at end of input.
            if ((c == 'R' || c == 'r')
                    && text.regionMatches(true, pos, "Rem", 0, 3)
                    && (pos + 3 >= len || text.charAt(pos + 3) == ' ' || text.charAt(pos + 3) == '\r')) {
                String remText = (pos + 3 < len) ? text.substring(pos + 3) : "";
                tokens.add(new AmosToken.Rem(remText));
                break;
            }

            // Numeric literals (hex, binary, or decimal/float)
            if (c == '$' || c == '%'
                    || Character.isDigit(c)
                    || (c == '.' && pos + 1 < len && Character.isDigit(text.charAt(pos + 1)))) {
                int[] end = {pos};
                AmosToken numTok = parseNumericLiteral(text, pos, end);
                if (numTok != null) {
                    tokens.add(numTok);
                    pos = end[0];
                    continue;
                }
            }

            // Try to match a keyword (maximal munch)
            int[] end = {pos};
            AmosToken kwTok = parseKeyword(text, pos, end);
            if (kwTok != null) {
                tokens.add(kwTok);
                pos = end[0];
                continue;
            }

            // Operator / punctuation (try two-char compound first, e.g. >=, <=, <>)
            int[] opEnd = {pos};
            AmosToken opTok = parseOperator(text, pos, opEnd);
            if (opTok != null) {
                tokens.add(opTok);
                pos = opEnd[0];
                continue;
            }

            AmosToken identTok = parseIdentifier(text, pos, end);
            if (identTok != null) {
                tokens.add(identTok);
                pos = end[0];
                continue;
            }

            // Unknown byte/char: skip to avoid infinite loops on unsupported syntax
            pos++;
        }

        return tokens;
    }

    // -------------------------------------------------------------------------
    // Keyword matching (maximal munch, up to 3 words)
    // -------------------------------------------------------------------------

    /**
     * Tries to match the longest keyword starting at {@code start} in {@code text}.
     * Keywords may span multiple space-separated words (e.g. "Set Double Precision").
     * Sets {@code end[0]} to the position just past the matched keyword.
     */
    private AmosToken parseKeyword(String text, int start, int[] end) {
        // Collect up to 3 word boundaries
        int[] wordEnds = new int[3];
        int wordCount = 0;
        int pos = start;
        int len = text.length();

        while (wordCount < 3 && pos < len) {
            // Skip space before next word (only after first word)
            if (wordCount > 0) {
                if (pos >= len || text.charAt(pos) != ' ') break;
                pos++; // consume the space
                if (pos >= len || text.charAt(pos) == ' ') break; // double-space → stop
            }

            // Read word characters (letters, digits, underscore, $, #).
            // '#' is a terminal keyword char: include it but stop reading after it
            // (e.g. "Input #" is one word, and "1" after '#' is the channel number).
            int wStart = pos;
            while (pos < len && isKeywordChar(text.charAt(pos))) {
                char kc = text.charAt(pos);
                pos++;
                if (kc == '#') break; // '#' ends the keyword word
            }
            if (pos == wStart) break; // nothing read

            wordEnds[wordCount++] = pos;
        }

        // Try longest match first (3 words, 2 words, 1 word)
        for (int n = wordCount; n >= 1; n--) {
            String candidate = text.substring(start, wordEnds[n - 1]);
            if (tokenTable.lookup(candidate) == null) continue;

            end[0] = wordEnds[n - 1];

            // Count comma groups after the keyword to select the right signature form.
            int commaGroups = countCommaGroups(text, wordEnds[n - 1]);
            Integer key = tokenTable.selectKey(candidate, commaGroups);
            if (key == null) continue;

            AmosToken tok = keyToToken(key);
                // Track context for the next identifier token
                if (tok instanceof AmosToken.Keyword kw) {
                    int v = kw.value();
                    // Goto / Gosub / Resume / Pop Proc keyword → next ident is a LabelRef
                    nextIdentIsLabelRef = (v == 0x02A8 || v == 0x02C8 || v == 0x02EE || v == 0x0300);
                    // Dim keyword → next variable is an array variable
                    nextVarIsArray = (v == 0x0640);
                    // Procedure keyword → next ident is the procedure definition name
                    nextIdentIsProcDef = (v == 0x0376);
                } else {
                    nextIdentIsLabelRef = false;
                    nextVarIsArray = false;
                    nextIdentIsProcDef = false;
                }
                return tok;
        }

        return null;
    }

    /**
     * Counts the number of "comma groups" that follow position {@code start} in {@code text}.
     *
     * <p>A comma group is a sequence of characters between two commas (or between the
     * start of the argument list and the first comma).  Commas inside parentheses or
     * brackets are not counted (they belong to a nested expression).
     *
     * <p>Returns 0 if nothing that looks like an argument follows the keyword.  Returns
     * commaCount + 1 if at least one argument starts there.
     *
     * <p>Only characters that can start an argument (letter, digit, {@code (}, {@code $},
     * {@code %}, {@code "}, {@code '}) trigger {@code hasArg = true}.  Operators such as
     * {@code -} that follow a keyword (e.g. {@code Screen Height-1}) mean the keyword
     * itself takes no argument — the operator applies to its return value.
     */
    private static int countCommaGroups(String text, int start) {
        int len = text.length();
        int pos = start;
        // Skip leading spaces
        while (pos < len && text.charAt(pos) == ' ') pos++;
        if (pos >= len) return 0;

        char first = text.charAt(pos);
        // Argument starters: identifier char, digit, open-paren, $, %, ", '
        boolean hasArg = Character.isLetterOrDigit(first) || first == '(' || first == '$'
                || first == '%' || first == '"' || first == '\'';
        if (!hasArg) return 0;

        // If the keyword is invoked as a function — e.g. Zone(3,x,y) — the args are
        // inside the outer parentheses.  Count commas at depth 1 (inside the outer ())
        // rather than depth 0, and stop when the outer ) closes.
        boolean functionCall = (first == '(');
        int countDepth = functionCall ? 1 : 0;

        int commas = 0;
        int depth = 0;
        for (int i = pos; i < len; i++) {
            char c = text.charAt(i);
            if (c == '(' || c == '[') {
                depth++;
            } else if (c == ')' || c == ']') {
                depth--;
                if (depth < countDepth) break; // exited the function-call parens (or depth < 0)
            } else if (c == ':' && depth == 0) {
                break; // statement separator
            } else if (c == ',' && depth == countDepth) {
                commas++;
            }
        }
        return commas + 1;
    }

    private static boolean isKeywordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$' || c == '#';
    }

    // -------------------------------------------------------------------------
    // Identifier parsing
    // -------------------------------------------------------------------------

    private AmosToken parseIdentifier(String text, int start, int[] end) {
        int pos = start;
        int len = text.length();

        char first = text.charAt(pos);
        if (!(Character.isLetter(first) || first == '_')) return null;

        pos++;
        while (pos < len && isIdentifierChar(text.charAt(pos))) pos++;

        int nameEnd = pos;
        String name = text.substring(start, nameEnd);

        AmosToken.VarType type = AmosToken.VarType.INTEGER;
        if (pos < len) {
            char suffix = text.charAt(pos);
            if (suffix == '$') {
                type = AmosToken.VarType.STRING;
                pos++;
            } else if (suffix == '#') {
                type = AmosToken.VarType.FLOAT;
                pos++;
            }
        }

        end[0] = pos;

        // Label definition: NAME: where NAME is the first token on this line
        // We detect it by looking for ':' right after (no suffix was consumed) and no tokens yet.
        if (type == AmosToken.VarType.INTEGER && pos < len && text.charAt(pos) == ':'
                && (pos + 1 >= len || text.charAt(pos + 1) != '=')) {
            end[0] = pos + 1; // consume the ':'
            return new AmosToken.Label(name);
        }

        // Procedure definition: after the Procedure keyword the name is a Variable with flags=0x80
        if (nextIdentIsProcDef) {
            nextIdentIsProcDef = false;
            return new AmosToken.Variable(name, type, false, 0x80);
        }

        // LabelRef: after Goto, Gosub, etc.
        if (nextIdentIsLabelRef) {
            nextIdentIsLabelRef = false;
            return new AmosToken.LabelRef(name);
        }

        // ProcRef: known procedure name, OR identifier immediately followed by '['
        boolean isProcCall = procedureNames.contains(name.toLowerCase())
                || (pos < len && text.charAt(pos) == '[');
        if (isProcCall) {
            return new AmosToken.ProcRef(name);
        }

        // Array variable: flagged by a preceding Dim keyword, or if the name (with type suffix) is a known array
        String arraySuffix = type == AmosToken.VarType.STRING ? "$" : type == AmosToken.VarType.FLOAT ? "#" : "";
        boolean isArr = nextVarIsArray || arrayVarNames.contains(name.toLowerCase() + arraySuffix);
        if (nextVarIsArray) nextVarIsArray = false;

        return new AmosToken.Variable(name, type, isArr);
    }

    private static boolean isIdentifierChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // -------------------------------------------------------------------------
    // Numeric literal parsing
    // -------------------------------------------------------------------------

    /**
     * Parses a numeric literal starting at {@code start} in {@code text}.
     * Does NOT handle a leading minus sign; callers must emit a minus operator token first.
     * Sets {@code end[0]} to the position just past the parsed literal.
     *
     * Supported formats:
     *   $HEXDIGITS          → HexInt
     *   %BINARYDIGITS       → BinaryInt
     *   DIGITS              → DecimalInt
     *   DIGITS.DIGITS       → Flt
     *   DIGITS[.DIGITS][E[+|-]DIGITS]  → Flt   (AMOS prints space before E: "1.23 E+5")
     *   value#              → Dbl
     */
    private AmosToken parseNumericLiteral(String text, int start, int[] end) {
        int pos = start;
        int len = text.length();
        char c = text.charAt(pos);

        // Hex literal: $XXXX
        if (c == '$') {
            pos++;
            int hexStart = pos;
            while (pos < len && isHexDigit(text.charAt(pos))) pos++;
            if (pos == hexStart) return null;
            end[0] = pos;
            return new AmosToken.HexInt((int) Long.parseLong(text.substring(hexStart, pos), 16));
        }

        // Binary literal: %BBBB
        if (c == '%') {
            pos++;
            int binStart = pos;
            while (pos < len && (text.charAt(pos) == '0' || text.charAt(pos) == '1')) pos++;
            if (pos == binStart) return null;
            end[0] = pos;
            return new AmosToken.BinaryInt(Integer.parseInt(text.substring(binStart, pos), 2));
        }

        // Decimal / float
        int numStart = pos;
        boolean isFloat = false;

        // Integer part
        while (pos < len && Character.isDigit(text.charAt(pos))) pos++;

        // Fractional part
        if (pos < len && text.charAt(pos) == '.') {
            isFloat = true;
            pos++;
            while (pos < len && Character.isDigit(text.charAt(pos))) pos++;
        }

        // Exponent: E/e, optionally with a space before it (AMOS prints "1.23 E+18")
        int ePos = -1;
        if (pos < len && text.charAt(pos) == ' '
                && pos + 1 < len
                && (text.charAt(pos + 1) == 'E' || text.charAt(pos + 1) == 'e')) {
            ePos = pos + 1;
        } else if (pos < len && (text.charAt(pos) == 'E' || text.charAt(pos) == 'e')) {
            ePos = pos;
        }

        if (ePos >= 0) {
            int expPos = ePos + 1; // skip E/e
            if (expPos < len && (text.charAt(expPos) == '+' || text.charAt(expPos) == '-')) expPos++;

            int expDigitsStart = expPos;
            while (expPos < len && Character.isDigit(text.charAt(expPos))) expPos++;

            // Only consume exponent if at least one exponent digit exists.
            if (expPos > expDigitsStart) {
                isFloat = true;
                pos = expPos;
            }
        }

        // Double suffix '#'
        boolean isDouble = pos < len && text.charAt(pos) == '#';
        if (isDouble) {
            isFloat = true;
            pos++;
        }

        if (pos == numStart) return null;

        // Build the Java-parseable number string (remove the AMOS space-before-E)
        String numStr = text.substring(numStart, pos);
        if (isDouble) numStr = numStr.substring(0, numStr.length() - 1); // strip '#'
        String javaStr = numStr.replace(" E", "E").replace(" e", "e");

        end[0] = pos;

        if (isDouble) {
            return new AmosToken.Dbl(Double.parseDouble(javaStr));
        } else if (isFloat) {
            return new AmosToken.Flt(Float.parseFloat(javaStr));
        } else {
            return new AmosToken.DecimalInt(Integer.parseInt(javaStr));
        }
    }

    // -------------------------------------------------------------------------
    // Single-character operators
    // -------------------------------------------------------------------------

    private AmosToken parseSingleCharOp(char c) {
        // Try two-char operators first (e.g. >=, <=, <>)
        // This avoids emitting two tokens where AMOS has a single compound-operator token
        if ((c == '>' || c == '<' || c == '=') && /* caller ensures pos is valid */ true) {
            // We can only peek if we have access to pos+1; but we don't here.
            // Instead, in parseTokens we call a two-char variant.
        }
        String s = String.valueOf(c);
        Integer key = tokenTable.lookup(s);
        if (key != null) return keyToToken(key);
        return null;
    }

    /**
     * Tries to match a (possibly two-char) operator at {@code pos} in {@code text}.
     * Two-char operators like {@code >=}, {@code <=}, {@code <>} are tried first.
     * Sets {@code end[0]} to position past the matched operator.
     */
    private AmosToken parseOperator(String text, int pos, int[] end) {
        int len = text.length();
        char c = text.charAt(pos);
        // Try two-char first
        if (pos + 1 < len) {
            String two = text.substring(pos, pos + 2);
            Integer key = tokenTable.lookup(two);
            if (key != null) {
                end[0] = pos + 2;
                return keyToToken(key);
            }
        }
        // Fall back to single char
        String one = String.valueOf(c);
        Integer key = tokenTable.lookup(one);
        if (key != null) {
            end[0] = pos + 1;
            return keyToToken(key);
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private AmosToken keyToToken(int key) {
        int slot = key >>> 16;
        int offset = key & 0xFFFF;
        if (slot == 0) {
            return new AmosToken.Keyword(offset);
        } else {
            return new AmosToken.ExtKeyword(slot, offset);
        }
    }

    private static boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }
}
