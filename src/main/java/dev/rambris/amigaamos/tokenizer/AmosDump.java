/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Dumps and diffs AMOS binary files at the token level.
 *
 * <p>Use {@link #dump} to print a human-readable token listing of a single
 * file, or {@link #diff} to compare two files and report the first point of
 * divergence in each differing line.</p>
 *
 * <p>The output is deliberately verbose so that the raw hex values are always
 * visible alongside the decoded interpretation — this makes it easy to spot
 * where the encoder makes a different choice than the original AMOS tokenizer.</p>
 */
public class AmosDump {

    private final TokenTable tokenTable;

    public AmosDump() {
        tokenTable = new TokenTable();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /** Dumps a single AMOS binary file to {@code out}. */
    public void dump(Path path, PrintStream out) throws Exception {
        var data = Files.readAllBytes(path);
        out.println("=== " + path + " ===");
        printHeader(data, out);
        var lines = extractLines(data);
        out.printf("Lines: %d%n%n", lines.size());
        for (int i = 0; i < lines.size(); i++) {
            printLine(i, lines.get(i), out);
        }
    }

    /**
     * Compares two AMOS binary files and prints a side-by-side diff for every
     * line that differs.  Lines that are identical are skipped.
     */
    public void diff(Path expectedPath, Path actualPath, PrintStream out) throws Exception {
        var exp = Files.readAllBytes(expectedPath);
        var act = Files.readAllBytes(actualPath);

        out.println("=== DIFF ===");
        out.println("  EXP: " + expectedPath);
        out.println("  ACT: " + actualPath);
        out.println();

        // Version header (bytes 0–11)
        boolean headerMatch = true;
        for (int i = 0; i < 12; i++) {
            if (i >= exp.length || i >= act.length || exp[i] != act[i]) {
                headerMatch = false;
                break;
            }
        }
        if (!headerMatch) {
            out.println("VERSION HEADER DIFFERS:");
            out.println("  EXP: " + headerString(exp));
            out.println("  ACT: " + headerString(act));
            out.println();
        }

        var expLines = extractLines(exp);
        var actLines = extractLines(act);

        int maxLines = Math.max(expLines.size(), actLines.size());
        if (expLines.size() != actLines.size()) {
            out.printf("LINE COUNT DIFFERS: exp=%d act=%d%n%n", expLines.size(), actLines.size());
        }

        int diffCount = 0;
        for (int i = 0; i < maxLines; i++) {
            var eLine = i < expLines.size() ? expLines.get(i) : new byte[0];
            var aLine = i < actLines.size() ? actLines.get(i) : new byte[0];
            if (!bytesEqual(eLine, aLine)) {
                diffCount++;
                printLineDiff(i, eLine, aLine, out);
            }
        }

        if (diffCount == 0) {
            out.println("Files are structurally identical.");
        } else {
            out.printf("%nLines with differences: %d / %d%n", diffCount, maxLines);
        }
    }

    // -------------------------------------------------------------------------
    // Printing
    // -------------------------------------------------------------------------

    private void printHeader(byte[] data, PrintStream out) {
        out.println("Version : " + headerString(data));
        if (data.length >= 20) {
            int codeLen = readU32(data, 16);
            out.printf("Code len: %d bytes%n", codeLen);
        }
    }

    private void printLine(int lineIdx, byte[] line, PrintStream out) {
        int wordCount = line[0] & 0xFF;
        int indent    = line[1] & 0xFF;
        out.printf("Line %3d  indent=%d  words=%d:%n", lineIdx, indent, wordCount);
        var tokens = parseTokens(line);
        for (ParsedToken t : tokens) {
            out.printf("  %s%n", t.format());
        }
        out.println();
    }

    private void printLineDiff(int lineIdx, byte[] eLine, byte[] aLine, PrintStream out) {
        int eWords  = eLine.length > 0 ? (eLine[0] & 0xFF) : 0;
        int aWords  = aLine.length > 0 ? (aLine[0] & 0xFF) : 0;
        int eIndent = eLine.length > 1 ? (eLine[1] & 0xFF) : 0;
        int aIndent = aLine.length > 1 ? (aLine[1] & 0xFF) : 0;

        out.printf("Line %3d  [DIFFER]%n", lineIdx);

        if (eWords != aWords || eIndent != aIndent) {
            out.printf("  header: exp(indent=%d words=%d) act(indent=%d words=%d)%n",
                    eIndent, eWords, aIndent, aWords);
        } else {
            out.printf("  header: indent=%d words=%d%n", eIndent, eWords);
        }

        // Parse tokens from both lines and diff them
        var expTokens = parseTokens(eLine);
        var actTokens = parseTokens(aLine);
        int maxTok = Math.max(expTokens.size(), actTokens.size());

        for (int t = 0; t < maxTok; t++) {
            var et = t < expTokens.size() ? expTokens.get(t) : null;
            var at = t < actTokens.size() ? actTokens.get(t) : null;
            boolean same = et != null && at != null && bytesEqual(et.raw, at.raw);
            var marker = same ? "    " : " <<<";
            if (same) {
                out.printf("  [%2d]%s  %s%n", t, marker, et.format());
            } else {
                String expStr = et != null ? et.format() : "<missing>";
                String actStr = at != null ? at.format() : "<missing>";
                out.printf("  [%2d]%s  EXP: %s%n", t, marker, expStr);
                out.printf("       %s  ACT: %s%n", "    ", actStr);
            }
        }
        out.println();
    }

    // -------------------------------------------------------------------------
    // Token parsing
    // -------------------------------------------------------------------------

    private List<ParsedToken> parseTokens(byte[] line) {
        var result = new ArrayList<ParsedToken>();
        int i = 2; // skip header bytes
        while (i + 1 < line.length) {
            int tok = readU16(line, i);
            int payloadSize = payloadSize(tok, line, i + 2);
            int totalSize = 2 + payloadSize;
            var raw = safeSlice(line, i, totalSize);
            result.add(new ParsedToken(tok, raw, line, i));
            i += totalSize;
            if (tok == 0x0000) break; // EOL
        }
        return result;
    }

    /**
     * Returns the number of payload bytes that follow the 2-byte token type
     * at {@code payloadStart} in {@code line}.
     */
    private int payloadSize(int tok, byte[] line, int payloadStart) {
        return switch (tok) {
            case 0x003E, 0x0036, 0x001E -> 4; // integer literals
            case 0x0046 -> 4;                  // float
            case 0x2B6A -> 8;                  // double
            case 0x0026, 0x002E -> {            // quoted strings
                if (payloadStart + 1 >= line.length) yield 0;
                int len = readU16(line, payloadStart);
                int total = 2 + len;
                yield (total % 2 != 0) ? total + 1 : total;
            }
            case 0x0652, 0x064A -> {            // REMs
                if (payloadStart + 1 >= line.length) yield 0;
                int len = line[payloadStart + 1] & 0xFF;
                int total = 2 + len;
                yield (total % 2 != 0) ? total + 1 : total;
            }
            case 0x0006, 0x000C, 0x0012, 0x0018 -> { // named tokens
                if (payloadStart + 2 >= line.length) yield 0;
                int n = line[payloadStart + 2] & 0xFF;
                yield 4 + n;
            }
            case 0x004E -> 4;                   // ExtKeyword
            case 0x0000 -> 0;                   // EOL
            default -> {
                int extra = tokenTable.extraBytesFor(tok);
                yield extra;
            }
        };
    }

    // -------------------------------------------------------------------------
    // ParsedToken — holds the raw bytes and knows how to format itself
    // -------------------------------------------------------------------------

    private static class ParsedToken {
        final int tok;
        final byte[] raw;      // includes the 2-byte type + payload
        final byte[] line;     // the full line (for payload reading)
        final int offset;      // byte offset of this token within line

        ParsedToken(int tok, byte[] raw, byte[] line, int offset) {
            this.tok    = tok;
            this.raw    = raw;
            this.line   = line;
            this.offset = offset;
        }

        String format() {
            int payloadStart = offset + 2;
            StringBuilder sb = new StringBuilder();

            // Token type
            sb.append(String.format("%04X", tok));

            // Payload hex
            if (raw.length > 2) {
                sb.append("  ");
                for (int i = 2; i < raw.length; i++) {
                    sb.append(String.format("%02X", raw[i] & 0xFF));
                    if (i < raw.length - 1) sb.append(' ');
                }
            }

            // Annotation
            sb.append("  ").append(annotate(payloadStart));
            return sb.toString();
        }

        private String annotate(int ps) {
            return switch (tok) {
                case 0x0000 -> "EOL";
                case 0x003E -> "Int(" + readS32(line, ps) + ")";
                case 0x0036 -> "Hex($" + Integer.toHexString(readS32(line, ps)).toUpperCase() + ")";
                case 0x001E -> "Bin(%" + Integer.toBinaryString(readS32(line, ps)) + ")";
                case 0x0046 -> "Flt(" + formatAmosFloat(readU32(line, ps)) + ")";
                case 0x2B6A -> "Dbl(" + Double.longBitsToDouble(readU64(line, ps)) + ")";
                case 0x0026 -> "Str(\"" + readStr(line, ps, readU16(line, ps)) + "\")";
                case 0x002E -> "Str('" + readStr(line, ps, readU16(line, ps)) + "')";
                case 0x0652 -> "Rem('" + readStr(line, ps + 2, line[ps + 1] & 0xFF) + "')";
                case 0x064A -> "Rem(\"" + readStr(line, ps + 2, line[ps + 1] & 0xFF) + "\")";
                case 0x004E -> {
                    if (ps + 3 < line.length) {
                        int slot   = line[ps] & 0xFF;
                        int extOff = readU16(line, ps + 2);
                        yield String.format("ExtKw(slot=%d off=%d/0x%04X)", slot, extOff, extOff);
                    }
                    yield "ExtKw(?)";
                }
                case 0x0006, 0x000C, 0x0012, 0x0018 -> {
                    var kind = switch (tok) {
                        case 0x0006 -> "Var";
                        case 0x000C -> "Label";
                        case 0x0012 -> "ProcRef";
                        case 0x0018 -> "LabelRef";
                        default     -> "?";
                    };
                    if (ps + 3 < line.length) {
                        int n     = line[ps + 2] & 0xFF;
                        int flags = line[ps + 3] & 0xFF;
                        var name = readStr(line, ps + 4, n);
                        var type = switch (flags & 0x03) {
                            case 0x01 -> "#";
                            case 0x02 -> "$";
                            default   -> "";
                        };
                        var arr = (flags & 0x40) != 0 ? "()" : "";
                        var proc = (flags & 0x80) != 0 ? "[proc-def]" : "";
                        yield kind + "(" + name + type + arr + proc + ")";
                    }
                    yield kind + "(?)";
                }
                default -> "Kw(0x" + String.format("%04X", tok) + ")";
            };
        }

        private static int readS32(byte[] d, int o) {
            if (o + 3 >= d.length) return 0;
            return (d[o] << 24) | ((d[o+1] & 0xFF) << 16) | ((d[o+2] & 0xFF) << 8) | (d[o+3] & 0xFF);
        }
        private static int readU32(byte[] d, int o) {
            if (o + 3 >= d.length) return 0;
            return ((d[o] & 0xFF) << 24) | ((d[o+1] & 0xFF) << 16) | ((d[o+2] & 0xFF) << 8) | (d[o+3] & 0xFF);
        }
        private static long readU64(byte[] d, int o) {
            long hi = readU32(d, o) & 0xFFFFFFFFL;
            long lo = readU32(d, o + 4) & 0xFFFFFFFFL;
            return (hi << 32) | lo;
        }
        private static int readU16(byte[] d, int o) {
            if (o + 1 >= d.length) return 0;
            return ((d[o] & 0xFF) << 8) | (d[o+1] & 0xFF);
        }
        private static String readStr(byte[] d, int o, int len) {
            if (o >= d.length) return "";
            int actual = Math.min(len, d.length - o);
            var sb = new StringBuilder();
            for (int i = 0; i < actual; i++) {
                byte b = d[o + i];
                if (b == 0) break;
                char c = (char)(b & 0xFF);
                sb.append((c >= 0x20 && c < 0x7F) ? c : '?');
            }
            return sb.toString();
        }
        private static String formatAmosFloat(int bits) {
            int e = bits & 0x7F;
            if (e == 0) return "0.0";
            float mantissa = (bits >>> 8) & 0xFFFFFFL;
            float v = (float)(mantissa * Math.pow(2, e - 88));
            return Float.toString(v);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static List<byte[]> extractLines(byte[] data) {
        var lines = new ArrayList<byte[]>();
        int pos = 20; // 16-byte header + 4-byte code length
        while (pos < data.length) {
            int wc = data[pos] & 0xFF;
            if (wc == 0) break;
            int lineLen = wc * 2;
            if (pos + lineLen > data.length) break;
            lines.add(java.util.Arrays.copyOfRange(data, pos, pos + lineLen));
            pos += lineLen;
        }
        return lines;
    }

    private static String headerString(byte[] data) {
        if (data.length < 12) return "<truncated>";
        var sb = new StringBuilder();
        for (int i = 0; i < 12; i++) {
            char c = (char)(data[i] & 0xFF);
            sb.append((c >= 0x20 && c < 0x7F) ? c : '.');
        }
        return sb.toString();
    }

    private static int readU16(byte[] d, int o) {
        return ((d[o] & 0xFF) << 8) | (d[o + 1] & 0xFF);
    }

    private static int readU32(byte[] d, int o) {
        return ((d[o] & 0xFF) << 24) | ((d[o+1] & 0xFF) << 16) | ((d[o+2] & 0xFF) << 8) | (d[o+3] & 0xFF);
    }

    private static byte[] safeSlice(byte[] d, int from, int len) {
        int actual = Math.min(len, d.length - from);
        return java.util.Arrays.copyOfRange(d, from, from + actual);
    }

    private static boolean bytesEqual(byte[] a, byte[] b) {
        return java.util.Arrays.equals(a, b);
    }
}
