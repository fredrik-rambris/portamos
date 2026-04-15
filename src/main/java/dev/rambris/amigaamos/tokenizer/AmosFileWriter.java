/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.bank.AmosBank;
import dev.rambris.amigaamos.tokenizer.model.AmosVersion;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

/**
 * Writes the complete AMOS binary file format.
 *
 * File structure:
 *   Bytes  0-15: 16-byte version header
 *   Bytes 16-19: uint32 big-endian code length (n)
 *   Bytes 20 to 20+n-1: tokenized code (sequence of encoded lines)
 *   Bytes 20+n onwards: "AmBs" + uint16 bank count (6 bytes minimum for zero banks)
 */
class AmosFileWriter {

    // -------------------------------------------------------------------------
    // Procedure post-processing constants
    // -------------------------------------------------------------------------

    /** Binary token value for the {@code Procedure} keyword. */
    private static final int TOK_PROCEDURE = 0x0376;
    /** Binary token value for the {@code End Proc} keyword. */
    private static final int TOK_END_PROC  = 0x0390;

    /** Procedure flag: procedure is folded in the editor (bit 7). */
    private static final int PROC_FOLDED = 0x80;

    /**
     * Byte offset within a Procedure line for the size field.
     *
     * <p>Line layout: [wordCount:1][indent:1][0x03:1][0x76:1][size:4][key3:2][flags:1][seedLo:1][...variable token...][EOL:2]
     */
    private static final int PROC_OFF_SIZE  = 4;
    private static final int PROC_OFF_FLAGS = 10;

    /**
     * Constant used in the size-field formula.
     * {@code size = procLineLen + innerLinesLen - PROC_SIZE_BIAS}
     *
     * <p>From the wiki: {@code start_of_end_proc = start_of_proc_line + 8 + size},
     * which means {@code size + 14 = procLineLen + innerLinesLen}.
     */
    private static final int PROC_SIZE_BIAS = 14;

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Writes all encoded lines into a complete AMOS binary file.
     *
     * @param version      the AMOS version to use for the file header
     * @param encodedLines the list of already-encoded lines (from BinaryEncoder.encodeLine)
     * @param banks        data banks to attach (may be empty)
     * @param foldProcedures whether to set the fold flag on all procedures
     * @return the complete binary file as a byte array
     */
    byte[] write(AmosVersion version, List<byte[]> encodedLines, List<AmosBank> banks,
                 boolean foldProcedures) {
        postProcessProcedures(encodedLines, foldProcedures);

        int codeLen = encodedLines.stream().mapToInt(l -> l.length).sum();

        var out = new ByteArrayOutputStream(22 + codeLen + 6);

        // 16-byte version header
        var header = version.headerBytes();
        try {
            out.write(header);

            // Code length: 4 bytes big-endian
            out.write((codeLen >> 24) & 0xFF);
            out.write((codeLen >> 16) & 0xFF);
            out.write((codeLen >> 8) & 0xFF);
            out.write(codeLen & 0xFF);

            // Code section: all encoded lines
            for (var line : encodedLines) {
                out.write(line);
            }

            // AmBs section: magic + uint16 bank count
            out.write('A');
            out.write('m');
            out.write('B');
            out.write('s');
            int bankCount = banks.size();
            out.write((bankCount >> 8) & 0xFF);
            out.write(bankCount & 0xFF);

            // Serialize each bank
            if (!banks.isEmpty()) {
                for (var bank : banks) {
                    out.write(bank.writer().toBytes(bank));
                }
            }
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new RuntimeException("Unexpected IO error", e);
        }

        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Procedure post-processing
    // -------------------------------------------------------------------------

    /**
     * Scans {@code lines} for {@code Procedure} tokens and sets the fold flag and
     * size field on each one.
     *
     * <p>The size field must be set for every procedure so that AMOS can navigate
     * past the procedure body (e.g. when it is folded in the editor).
     * Formula (from the file-format wiki):
     * {@code start_of_end_proc = start_of_proc_line + 8 + size}
     * ↔ {@code size = procLineLen + innerLinesLen - PROC_SIZE_BIAS}
     *
     * <p>When {@code foldByDefault} is {@code true}, bit 7 (folded) is set on every
     * procedure.
     */
    private static void postProcessProcedures(List<byte[]> lines, boolean foldByDefault) {
        for (int i = 0; i < lines.size(); i++) {
            var line = lines.get(i);
            if (!isProcLine(line)) continue;

            line[PROC_OFF_FLAGS] = (byte)(foldByDefault ? PROC_FOLDED : 0);

            // Compute and store the size field for all procedures so AMOS can
            // navigate the file correctly regardless of fold state.
            var endIdx   = findEndProc(lines, i);
            var innerLen = 0;
            for (int j = i + 1; j <= endIdx; j++) innerLen += lines.get(j).length;
            var size = line.length + innerLen - PROC_SIZE_BIAS;

            line[PROC_OFF_SIZE    ] = (byte)((size >> 24) & 0xFF);
            line[PROC_OFF_SIZE + 1] = (byte)((size >> 16) & 0xFF);
            line[PROC_OFF_SIZE + 2] = (byte)((size >>  8) & 0xFF);
            line[PROC_OFF_SIZE + 3] = (byte)( size        & 0xFF);
        }
    }

    /** Returns {@code true} if {@code line} begins with the Procedure token ($0376). */
    private static boolean isProcLine(byte[] line) {
        return line.length >= 14
            && (line[2] & 0xFF) == (TOK_PROCEDURE >> 8)
            && (line[3] & 0xFF) == (TOK_PROCEDURE & 0xFF);
    }

    /** Returns {@code true} if {@code line} begins with the End Proc token ($0390). */
    private static boolean isEndProcLine(byte[] line) {
        return line.length >= 6
            && (line[2] & 0xFF) == (TOK_END_PROC >> 8)
            && (line[3] & 0xFF) == (TOK_END_PROC & 0xFF);
    }

    /**
     * Finds the index of the {@code End Proc} line that closes the {@code Procedure}
     * at {@code procIdx}, tracking depth to handle nested procedures correctly.
     *
     * @throws IllegalStateException if no matching End Proc is found before end of file
     */
    private static int findEndProc(List<byte[]> lines, int procIdx) {
        var depth = 1;
        for (int i = procIdx + 1; i < lines.size(); i++) {
            var l = lines.get(i);
            if (isProcLine(l))                              depth++;
            else if (isEndProcLine(l) && --depth == 0)     return i;
        }
        throw new IllegalStateException("No End Proc found for Procedure at line index " + procIdx);
    }
}
