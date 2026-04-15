/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosVersion;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for procedure flag post-processing in {@link AmosFileWriter}.
 *
 * <p>Verifies that:
 * <ul>
 *   <li>Procedures are <em>not</em> folded by default</li>
 *   <li>{@link Tokenizer#withFoldedProcedures()} sets the fold bit on all procedures</li>
 *   <li>The size field is always written so AMOS can navigate past the procedure body</li>
 * </ul>
 */
class ProcedureFlagsTest {

    // Byte offsets within a Procedure binary line
    private static final int OFF_FLAGS = 10;
    private static final int OFF_SIZE  = 4;   // 4 bytes: OFF_SIZE..OFF_SIZE+3

    // Flag bit value
    private static final int FOLDED = 0x80;

    private static final String SRC_PLAIN = """
            Procedure MYTEST
              Print "hello"
            End Proc""";

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Encodes source and returns the first Procedure line found in the binary. */
    private static byte[] procLine(Tokenizer t, String src) {
        var binary = t.tokenizeToBytes(src);
        var pos = 20; // after 16-byte header + 4-byte code length
        while (pos < binary.length) {
            var wc = binary[pos] & 0xFF;
            if (wc == 0) break;
            var len = wc * 2;
            if (pos + len > binary.length) break;
            if (len >= 14
                    && (binary[pos + 2] & 0xFF) == 0x03
                    && (binary[pos + 3] & 0xFF) == 0x76) {
                return Arrays.copyOfRange(binary, pos, pos + len);
            }
            pos += len;
        }
        return null;
    }

    private static int flags(byte[] procLine) {
        return procLine[OFF_FLAGS] & 0xFF;
    }

    private static int sizeField(byte[] procLine) {
        return ((procLine[OFF_SIZE    ] & 0xFF) << 24)
             | ((procLine[OFF_SIZE + 1] & 0xFF) << 16)
             | ((procLine[OFF_SIZE + 2] & 0xFF) <<  8)
             |  (procLine[OFF_SIZE + 3] & 0xFF);
    }

    // -------------------------------------------------------------------------
    // Fold flag tests
    // -------------------------------------------------------------------------

    @Test
    void default_procedureIsNotFolded() {
        var t = new Tokenizer(AmosVersion.PRO_101);
        var line = procLine(t, SRC_PLAIN);
        assertNotNull(line);
        assertEquals(0x00, flags(line), "Flags should be 0x00 when no fold option is set");
    }

    @Test
    void foldOption_setsOnlyFoldBit() {
        var t = new Tokenizer(AmosVersion.PRO_101).withFoldedProcedures();
        var line = procLine(t, SRC_PLAIN);
        assertNotNull(line);
        assertEquals(FOLDED, flags(line),
                "withFoldedProcedures() should set only bit 7");
    }

    // -------------------------------------------------------------------------
    // Size field tests
    // -------------------------------------------------------------------------

    @Test
    void sizeField_isAlwaysSetEvenWhenNotFolded() {
        var t = new Tokenizer(AmosVersion.PRO_101);
        var line = procLine(t, SRC_PLAIN);
        assertNotNull(line);
        assertTrue(sizeField(line) > 0,
                "Size field must be non-zero so AMOS can navigate past the procedure body");
    }

    @Test
    void sizeField_matchesExpectedFormula() {
        // Verify size = procLineLen + innerLinesLen - 14.
        // Inner lines: Print "hello" + End Proc.
        var binary = new Tokenizer(AmosVersion.PRO_101).tokenizeToBytes(SRC_PLAIN);

        var lineBytes = new java.util.ArrayList<byte[]>();
        var pos = 20;
        while (pos < binary.length) {
            var wc = binary[pos] & 0xFF;
            if (wc == 0) break;
            var len = wc * 2;
            lineBytes.add(Arrays.copyOfRange(binary, pos, pos + len));
            pos += len;
        }

        // Find the Procedure line
        var procIdx = -1;
        for (int i = 0; i < lineBytes.size(); i++) {
            var l = lineBytes.get(i);
            if (l.length >= 14 && (l[2] & 0xFF) == 0x03 && (l[3] & 0xFF) == 0x76) {
                procIdx = i;
                break;
            }
        }
        assertNotEquals(-1, procIdx, "Procedure line not found");

        // Find End Proc line
        var endProcIdx = -1;
        for (int i = procIdx + 1; i < lineBytes.size(); i++) {
            var l = lineBytes.get(i);
            if (l.length >= 6 && (l[2] & 0xFF) == 0x03 && (l[3] & 0xFF) == 0x90) {
                endProcIdx = i;
                break;
            }
        }
        assertNotEquals(-1, endProcIdx, "End Proc line not found");

        var procLine = lineBytes.get(procIdx);
        var innerLen = 0;
        for (int i = procIdx + 1; i <= endProcIdx; i++) innerLen += lineBytes.get(i).length;

        var expectedSize = procLine.length + innerLen - 14;
        assertEquals(expectedSize, sizeField(procLine),
                "Size field should equal procLineLen + innerLinesLen - 14");
    }

    @Test
    void multipleProcedures_allGetCorrectFoldFlag() {
        var src = """
                Procedure FIRST
                  Print "a"
                End Proc
                Procedure SECOND
                  Print "b"
                End Proc""";

        var binary = new Tokenizer(AmosVersion.PRO_101).withFoldedProcedures()
                .tokenizeToBytes(src);

        var pos = 20;
        var results = new java.util.ArrayList<Integer>();
        while (pos < binary.length) {
            var wc = binary[pos] & 0xFF;
            if (wc == 0) break;
            var len = wc * 2;
            if (len >= 14
                    && (binary[pos + 2] & 0xFF) == 0x03
                    && (binary[pos + 3] & 0xFF) == 0x76) {
                results.add(binary[pos + OFF_FLAGS] & 0xFF);
            }
            pos += len;
        }

        assertEquals(2, results.size(), "Should find two Procedure lines");
        assertEquals(FOLDED, results.get(0), "FIRST: fold bit set");
        assertEquals(FOLDED, results.get(1), "SECOND: fold bit set");
    }
}
