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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Reads a complete AMOS binary file (`.AMOS`) into an {@link AmosFile}.
 *
 * <p>This is the inverse of {@link AmosFileWriter}: given the raw bytes of a
 * {@code .AMOS} file it reconstructs the {@link AmosVersion}, the sequence of
 * {@link AmosLine}s, and notes how many banks are attached (bank decoding is
 * handled separately — see {@code export} command).
 *
 * <p>File layout (all integers big-endian):
 * <pre>
 *   [16]   version header
 *   [4]    code length in bytes
 *   [n]    code section: sequence of variable-length encoded lines
 *   [4]    "AmBs" magic
 *   [2]    bank count (may be 0)
 *   [...]  bank data (not decoded here)
 * </pre>
 */
class AmosFileReader {

    private final BinaryDecoder decoder;

    AmosFileReader(TokenTable tokenTable) {
        this.decoder = new BinaryDecoder(tokenTable);
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Reads a {@code .AMOS} file from {@code path}.
     */
    AmosFile read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads a {@code .AMOS} file from a raw byte array.
     */
    AmosFile read(byte[] data) {
        if (data.length < 20) {
            throw new IllegalArgumentException(
                    "File too short to be a valid AMOS binary (" + data.length + " bytes)");
        }

        var version = detectVersion(data);
        int codeLen = BinaryDecoder.readU32(data, 16);

        if (20 + codeLen > data.length) {
            throw new IllegalArgumentException(
                    "Code length " + codeLen + " exceeds file size " + data.length);
        }

        var lines = decodeLines(data, 20, codeLen);
        var compiledBody = extractCompiledBody(data, 20, codeLen);
        return new AmosFile(version, lines, List.of(), compiledBody);
    }

    // -------------------------------------------------------------------------
    // Compiled-body extraction
    // -------------------------------------------------------------------------

    /**
     * If the code section contains a sentinel byte (0x00) before its end, everything
     * from that sentinel to the end of the code section is the compiled procedure body
     * (AMOSPro Compiler output).  Returns {@code null} when the code section contains
     * only regular AMOS lines with no sentinel.
     *
     * <p>The returned array starts with the sentinel byte itself so that
     * {@link AmosFileWriter} can append it verbatim to reconstruct the section.
     */
    private static byte[] extractCompiledBody(byte[] data, int start, int codeLen) {
        int pos = start;
        int end = start + codeLen;
        while (pos < end) {
            int wc = data[pos] & 0xFF;
            if (wc == 0) {
                // Sentinel found: capture from here to end of code section.
                if (pos < end) return Arrays.copyOfRange(data, pos, end);
                break;
            }
            pos += wc * 2;
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Version detection
    // -------------------------------------------------------------------------

    /**
     * Detects the {@link AmosVersion} from the 16-byte file header.
     * Falls back to {@link AmosVersion#PRO_101} if the header is unrecognised.
     */
    static AmosVersion detectVersion(byte[] data) {
        // Read first 16 bytes as ASCII, replacing non-printable chars with '?'
        var sb = new StringBuilder(16);
        for (int i = 0; i < Math.min(16, data.length); i++) {
            char c = (char) (data[i] & 0xFF);
            sb.append(c);
        }
        var header = sb.toString();

        // AMOS Pro — both portamos output ("v") and original AMOS files ("V")
        if (header.startsWith("AMOS Pro101")) return AmosVersion.PRO_101;
        // AMOS Basic 1.34 uses uppercase "V"
        if (header.startsWith("AMOS Basic V1")) return AmosVersion.BASIC_134;
        // AMOS Basic 1.3 uses lowercase "v"
        if (header.startsWith("AMOS Basic v1")) return AmosVersion.BASIC_13;

        return AmosVersion.PRO_101;
    }

    // -------------------------------------------------------------------------
    // Line extraction and decoding
    // -------------------------------------------------------------------------

    /**
     * Extracts and decodes all lines from the code section.
     *
     * @param data    the full file byte array
     * @param start   byte offset of the first line (always 20)
     * @param codeLen total byte length of the code section
     */
    private List<AmosLine> decodeLines(byte[] data, int start, int codeLen) {
        var lines = new ArrayList<AmosLine>();
        int pos = start;
        int end = start + codeLen;

        while (pos < end) {
            int wc = data[pos] & 0xFF;
            if (wc == 0) break; // sentinel: end of code section
            int lineLen = wc * 2;
            if (pos + lineLen > end) break; // truncated

            var lineBytes = Arrays.copyOfRange(data, pos, pos + lineLen);
            lines.add(decoder.decodeLine(lineBytes));
            pos += lineLen;
        }

        return lines;
    }
}
