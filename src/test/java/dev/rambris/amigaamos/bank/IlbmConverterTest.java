/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.iff.codec.IlbmCodec;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class IlbmConverterTest {

    @BeforeAll
    static void quiet() {
        PaletteOptimizer.progress = false;
    }

    static Stream<Path> ilbmFiles() {
        return Stream.of(
                Path.of("src/test/resources/DefenderOfTheCrown2_Romantic_Fireplace.iff"),
                Path.of("src/test/resources/DeviousDesigns_Level01.iff"),
                Path.of("src/test/resources/DeviousDesigns_Level16.iff"),
                Path.of("src/test/resources/Spherical.iff"));
    }

    @ParameterizedTest(name = "optimized ILBM is smaller-or-equal and pixel-identical: {0}")
    @MethodSource("ilbmFiles")
    void optimizedIlbmIsSmallerAndIdentical(Path iff) throws Exception {
        var plain = new IlbmConverter().convert(iff);
        var opt = new IlbmConverter().withOptimize(true).convert(iff);

        System.out.printf("%-45s source=%6d  plain BODY=%6d  optimized BODY=%6d  (%.1f%%)%n",
                iff.getFileName(), iff.toFile().length(), plain.bodySize(), opt.bodySize(),
                100.0 * (plain.bodySize() - opt.bodySize()) / plain.bodySize());

        assertTrue(opt.bodySize() <= plain.bodySize(), "optimized BODY must not be larger");

        // Same RGB colour at every pixel
        var a = IlbmCodec.read(plain.ilbm());
        var b = IlbmCodec.read(opt.ilbm());
        assertEquals(a.bmhd().width(), b.bmhd().width());
        assertEquals(a.bmhd().height(), b.bmhd().height());
        assertEquals(a.camgMode(), b.camgMode());
        var pa = pixels(plain.ilbm());
        var pb = pixels(opt.ilbm());
        for (int y = 0; y < pa.length; y++) {
            for (int x = 0; x < pa[y].length; x++) {
                assertEquals(a.palette()[pa[y][x]], b.palette()[pb[y][x]],
                        "colour at (" + x + "," + y + ")");
            }
        }
    }

    private static int[][] pixels(byte[] ilbm) throws Exception {
        var tmp = java.nio.file.Files.createTempFile("ilbmconv", ".iff");
        try {
            java.nio.file.Files.write(tmp, ilbm);
            return IndexedPngWriter.readPixels(tmp).pixels();
        } finally {
            java.nio.file.Files.deleteIfExists(tmp);
        }
    }
}
