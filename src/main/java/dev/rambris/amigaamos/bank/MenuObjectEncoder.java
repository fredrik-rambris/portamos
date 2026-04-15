/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Encodes a Menu$ embedded-command string (as produced by {@link MenuObjectDecoder}) back
 * to a binary object blob suitable for storage in a {@link MenuNode}.
 *
 * <p>The input string is a mix of:
 * <ul>
 *   <li>Literal text characters — encoded as a single {@code TEXT (0x0004)} command.</li>
 *   <li>Embedded commands in {@code (XX...)} notation — encoded as the corresponding
 *       binary command code and parameters.</li>
 *   <li>Unrecognised hex escapes {@code [??XXXX]} — re-emitted verbatim (from decoding
 *       of unknown opcodes).</li>
 * </ul>
 *
 * <p>Command codes (see {@link MenuObjectDecoder} for the complete table):
 * <pre>
 *   BA x,y   → 0x0008 x y
 *   LI x,y   → 0x000C x y
 *   EL rx,ry → 0x0010 rx ry
 *   PA n     → 0x0014 n
 *   IN m,v   → 0x0018 m v
 *   BO n     → 0x001C n
 *   IC n     → 0x0020 n
 *   LO x,y   → 0x0024 x y
 *   OU n     → 0x0028 n
 *   SL n     → 0x002C n
 *   SF n     → 0x0030 n
 *   PR name  → 0x0034 delta name_bytes
 *   RE n     → 0x0038 n
 *   SS n     → 0x003C n
 * </pre>
 *
 * @see MenuObjectDecoder
 */
public class MenuObjectEncoder {

    /** Matches embedded commands: {@code (XX...)} or hex escapes {@code [??XXXX]}. */
    private static final Pattern TOKEN = Pattern.compile(
            "\\(([A-Z]{2})([ ,\\-0-9]*)\\)"   // (XX params)
            + "|\\[\\?\\?([0-9A-Fa-f]{4})\\]"  // [??XXXX]
    );

    private MenuObjectEncoder() {}

    /**
     * Encodes the given Menu$ string to a binary object blob.
     *
     * @param menuString the AMOS Menu$ string (embedded commands in {@code (XX...)} notation)
     * @return the binary blob with a leading big-endian uint16 total-size word, or {@code null}
     *         if {@code menuString} is {@code null}
     * @throws IllegalArgumentException if a command argument cannot be parsed
     */
    public static byte[] encode(String menuString) {
        if (menuString == null) return null;

        // Split the input into tokens: text segments and command segments
        var segments = tokenize(menuString);

        // Calculate the payload size first
        int payloadSize = 2; // the leading size word itself
        for (var seg : segments) {
            payloadSize += seg.encodedSize();
        }
        payloadSize += 2; // END marker

        try {
            var baos = new ByteArrayOutputStream(payloadSize);
            var out  = new DataOutputStream(baos);

            out.writeShort(payloadSize); // total size (including this word)

            for (var seg : segments) {
                seg.writeTo(out);
            }
            out.writeShort(0x0000); // END

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Encoding failed", e);
        }
    }

    // -------------------------------------------------------------------------
    // Tokenizer
    // -------------------------------------------------------------------------

    private static List<Segment> tokenize(String input) {
        var result = new ArrayList<Segment>();
        var m = TOKEN.matcher(input);
        int last = 0;

        while (m.find()) {
            // Text before this match
            if (m.start() > last) {
                result.add(new TextSegment(input.substring(last, m.start())));
            }

            if (m.group(3) != null) {
                // [??XXXX] hex escape
                result.add(new RawHexSegment(Integer.parseInt(m.group(3), 16)));
            } else {
                // (XX params)
                var mnemonic = m.group(1);
                var params   = m.group(2).trim();
                result.add(new CommandSegment(mnemonic, params));
            }

            last = m.end();
        }

        // Trailing text
        if (last < input.length()) {
            result.add(new TextSegment(input.substring(last)));
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Segment types
    // -------------------------------------------------------------------------

    private sealed interface Segment permits TextSegment, CommandSegment, RawHexSegment {
        int encodedSize();
        void writeTo(DataOutputStream out) throws IOException;
    }

    /** A run of literal characters → TEXT command (0x0004). */
    private record TextSegment(String text) implements Segment {
        @Override
        public int encodedSize() {
            var bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            int len = bytes.length;
            return 2 + 2 + len + (len & 1); // code + strlen + text + optional pad
        }

        @Override
        public void writeTo(DataOutputStream out) throws IOException {
            var bytes = text.getBytes(StandardCharsets.ISO_8859_1);
            int len = bytes.length;
            out.writeShort(0x0004);
            out.writeShort(len);
            out.write(bytes);
            if ((len & 1) == 1) out.writeByte(0); // pad to even
        }
    }

    /** A [??XXXX] hex escape for an unknown command code with no params. */
    private record RawHexSegment(int code) implements Segment {
        @Override
        public int encodedSize() { return 2; }

        @Override
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeShort(code);
        }
    }

    /** An embedded command like {@code (BA 30,15)}. */
    private static final class CommandSegment implements Segment {
        private final String mnemonic;
        private final String params;
        private final int    code;
        private final int    paramCount; // 0 = special (PR), 1 or 2 int params

        CommandSegment(String mnemonic, String params) {
            this.mnemonic   = mnemonic;
            this.params     = params;
            this.code       = codeOf(mnemonic);
            this.paramCount = paramCountOf(mnemonic);
        }

        @Override
        public int encodedSize() {
            return switch (paramCount) {
                case 1 -> 2 + 2;           // code + one short
                case 2 -> 2 + 4;           // code + two shorts
                default -> procEncodedSize(); // PR command
            };
        }

        @Override
        public void writeTo(DataOutputStream out) throws IOException {
            out.writeShort(code);
            switch (paramCount) {
                case 1 -> out.writeShort(parseOne(params));
                case 2 -> {
                    var p = parseTwo(params);
                    out.writeShort(p[0]);
                    out.writeShort(p[1]);
                }
                default -> writeProc(out);
            }
        }

        // PRoc: write delta:2 + nameLen:2 + nameBytes + pad_if_odd
        private int procEncodedSize() {
            if (params.isEmpty()) return 2 + 2; // code + delta (0) + empty
            var nameBytes = params.getBytes(StandardCharsets.ISO_8859_1);
            int nameLen = nameBytes.length;
            int padded  = nameLen + (nameLen & 1); // even-padded
            return 2 + 2 + 2 + padded; // code + delta + nameLen + name
        }

        private void writeProc(DataOutputStream out) throws IOException {
            if (params.isEmpty()) {
                out.writeShort(0); // delta = 0
                return;
            }
            var nameBytes = params.getBytes(StandardCharsets.ISO_8859_1);
            int nameLen = nameBytes.length;
            int padded  = nameLen + (nameLen & 1);
            int delta   = 2 + padded; // nameLen word + name bytes
            out.writeShort(delta);
            out.writeShort(nameLen);
            out.write(nameBytes);
            if ((nameLen & 1) == 1) out.writeByte(0);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int codeOf(String mnemonic) {
        return switch (mnemonic.toUpperCase()) {
            case "BA" -> 0x0008;
            case "LI" -> 0x000C;
            case "EL" -> 0x0010;
            case "PA" -> 0x0014;
            case "IN" -> 0x0018;
            case "BO" -> 0x001C;
            case "IC" -> 0x0020;
            case "LO" -> 0x0024;
            case "OU" -> 0x0028;
            case "SL" -> 0x002C;
            case "SF" -> 0x0030;
            case "PR" -> 0x0034;
            case "RE" -> 0x0038;
            case "SS" -> 0x003C;
            default   -> throw new IllegalArgumentException("Unknown menu command: " + mnemonic);
        };
    }

    /** Returns 1, 2, or -1 (variable-length PRoc). */
    private static int paramCountOf(String mnemonic) {
        return switch (mnemonic.toUpperCase()) {
            case "BA", "LI", "EL", "IN", "LO" -> 2;
            case "PR"                           -> -1;
            default                             -> 1;
        };
    }

    private static int parseOne(String params) {
        return Integer.parseInt(params.trim());
    }

    private static int[] parseTwo(String params) {
        // params may be "x,y" or " x,y" (with a leading space for coordinate commands)
        var parts = params.trim().split(",", 2);
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected two comma-separated params, got: " + params);
        }
        return new int[]{ Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
    }
}
