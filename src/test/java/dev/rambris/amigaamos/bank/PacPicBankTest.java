/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class PacPicBankTest {

    private static final Path SPACK_ABK = Path.of("src/test/resources/Spack.Abk");

    @Test
    void readsSpackBank() throws Exception {
        var bank = PacPicBankReader.read(SPACK_ABK);

        assertEquals(AmosBank.Type.PACPIC, bank.type());
        assertTrue(bank.isSpack());
        assertNotNull(bank.screenHeader());
        assertTrue(bank.picData().length > 24);

        // Packed Pac.Pic image must start with 0x06071963
        assertEquals(0x06, bank.picData()[0] & 0xFF);
        assertEquals(0x07, bank.picData()[1] & 0xFF);
        assertEquals(0x19, bank.picData()[2] & 0xFF);
        assertEquals(0x63, bank.picData()[3] & 0xFF);

        var sh = bank.screenHeader();
        assertEquals(32, sh.palette().length);
        assertTrue(sh.numColors() > 0);
        assertTrue(sh.numPlanes() > 0);
    }

    @Test
    void genericDispatchReadsPacPicBank() throws Exception {
        var bank = AmosBank.read(SPACK_ABK);

        assertInstanceOf(PacPicBank.class, bank);
        assertEquals(AmosBank.Type.PACPIC, bank.type());
    }

    @Test
    void spackRoundTripPreservesModel() throws Exception {
        var original = PacPicBankReader.read(SPACK_ABK);

        var writer = new PacPicBankWriter();
        var bytes = writer.toBytes(original);
        var readback = PacPicBankReader.read(bytes);

        assertEquals(original.bankNumber(), readback.bankNumber());
        assertEquals(original.chipRam(), readback.chipRam());
        assertEquals(original.isSpack(), readback.isSpack());
        assertArrayEquals(original.picData(), readback.picData());

        var osh = original.screenHeader();
        var rsh = readback.screenHeader();
        assertNotNull(osh);
        assertNotNull(rsh);
        assertEquals(osh.width(), rsh.width());
        assertEquals(osh.height(), rsh.height());
        assertEquals(osh.hardX(), rsh.hardX());
        assertEquals(osh.hardY(), rsh.hardY());
        assertEquals(osh.displayWidth(), rsh.displayWidth());
        assertEquals(osh.displayHeight(), rsh.displayHeight());
        assertEquals(osh.offsetX(), rsh.offsetX());
        assertEquals(osh.offsetY(), rsh.offsetY());
        assertEquals(osh.bplCon0(), rsh.bplCon0());
        assertEquals(osh.numColors(), rsh.numColors());
        assertEquals(osh.numPlanes(), rsh.numPlanes());
        assertArrayEquals(osh.palette(), rsh.palette());
    }

    @Test
    void exportWritesPngAndJson(@TempDir Path tmp) throws Exception {
        var bank = PacPicBankReader.read(SPACK_ABK);
        var jsonPath = tmp.resolve("spack.json");

        new PacPicBankExporter().export(bank, jsonPath);

        assertTrue(jsonPath.toFile().exists(), "json file");
        assertTrue(tmp.resolve("spack.png").toFile().exists(), "png sibling");
    }

    // -------------------------------------------------------------------------
    // Palette optimizer tests
    // -------------------------------------------------------------------------

    /**
     * All PacPic Abk files available as test resources.
     */
    static Stream<Path> pacPicBankFiles() {
        // Add more .Abk paths here as needed
        return Stream.of(
                Path.of("src/test/resources/Spack.Abk")
        );
    }

    /**
     * IFF ILBM images used for optimizer-from-image tests.
     * Add more here as scene images are collected; they live in src/test/resources/.
     */
    static Stream<org.junit.jupiter.params.provider.Arguments> ilbmImageFiles() {
        return Stream.of(
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("src/test/resources/DefenderOfTheCrown2_Romantic_Fireplace.iff"), 5),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("src/test/resources/DeviousDesigns_Level01.iff"), 4),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("src/test/resources/DeviousDesigns_Level16.iff"), 4),
                org.junit.jupiter.params.provider.Arguments.of(
                        Path.of("src/test/resources/Spherical.iff"), 4)
        );
    }

    @ParameterizedTest(name = "optimizer does not enlarge: {0}")
    @MethodSource("pacPicBankFiles")
    void paletteOptimizerNeverEnlargesOutput(Path abkPath) throws Exception {
        var bank = PacPicBankReader.read(abkPath);
        int planes = ((bank.picData()[PacPicFormat.OFF_PKPLAN] & 0xFF) << 8)
                     | (bank.picData()[PacPicFormat.OFF_PKPLAN + 1] & 0xFF);
        int srcX = ((bank.picData()[PacPicFormat.OFF_PKDX] & 0xFF) << 8)
                   | (bank.picData()[PacPicFormat.OFF_PKDX + 1] & 0xFF);
        srcX *= 8;
        int srcY = ((bank.picData()[PacPicFormat.OFF_PKDY] & 0xFF) << 8)
                   | (bank.picData()[PacPicFormat.OFF_PKDY + 1] & 0xFF);

        var pixels = PacPicDecoder.decompress(bank.picData());
        int numColors = 1 << planes;

        int baseline = PacPicEncoder.compress(pixels, srcX, srcY, planes).length;
        var result = PacPicPaletteOptimizer.optimize(pixels, numColors, srcX, srcY, planes);

        System.out.printf("%s: baseline=%d  optimized=%d  saving=%d bytes (%.1f%%)%n",
                abkPath.getFileName(), baseline, result.compressedSize(),
                baseline - result.compressedSize(),
                100.0 * (baseline - result.compressedSize()) / baseline);

        assertTrue(result.compressedSize() <= baseline,
                "Optimizer must not produce larger output than baseline");
    }

    @ParameterizedTest(name = "optimized pixels still decode identically: {0}")
    @MethodSource("pacPicBankFiles")
    void optimizedPixelsDecodeIdentically(Path abkPath) throws Exception {
        var bank = PacPicBankReader.read(abkPath);
        int planes = ((bank.picData()[PacPicFormat.OFF_PKPLAN] & 0xFF) << 8)
                     | (bank.picData()[PacPicFormat.OFF_PKPLAN + 1] & 0xFF);
        int srcXBytes = ((bank.picData()[PacPicFormat.OFF_PKDX] & 0xFF) << 8)
                        | (bank.picData()[PacPicFormat.OFF_PKDX + 1] & 0xFF);
        int srcX = srcXBytes * 8;
        int srcY = ((bank.picData()[PacPicFormat.OFF_PKDY] & 0xFF) << 8)
                   | (bank.picData()[PacPicFormat.OFF_PKDY + 1] & 0xFF);

        var origPixels = PacPicDecoder.decompress(bank.picData());
        int numColors = 1 << planes;

        var result = PacPicPaletteOptimizer.optimize(origPixels, numColors, srcX, srcY, planes);
        var recompressed = PacPicEncoder.compress(result.pixels(), srcX, srcY, planes);
        var decoded = PacPicDecoder.decompress(recompressed);

        assertEquals(origPixels.length, decoded.length, "height");
        assertEquals(origPixels[0].length, decoded[0].length, "width");

        // Every pixel in the decoded image must correspond to the same palette entry
        // as the original (via the permutation).
        var perm = result.permutation();
        for (int y = 0; y < origPixels.length; y++) {
            for (int x = 0; x < origPixels[y].length; x++) {
                int origIdx = origPixels[y][x];
                int newIdx = result.pixels()[y][x];
                // perm[newIdx] == origIdx  (permutation maps new → original)
                assertEquals(origIdx, perm[newIdx],
                        String.format("pixel (%d,%d): perm[%d]=%d, expected %d",
                                x, y, newIdx, perm[newIdx], origIdx));
            }
        }
    }

    @ParameterizedTest(name = "importer with --optimize produces valid bank: {0}")
    @MethodSource("pacPicBankFiles")
    void importerWithOptimizeProducesValidBank(Path abkPath, @TempDir Path tmp) throws Exception {
        var original = PacPicBankReader.read(abkPath);
        var jsonPath = tmp.resolve("bank.json");
        new PacPicBankExporter().export(original, jsonPath);

        var optimized = new PacPicBankImporter().withOptimize(true).importFrom(jsonPath);
        var unoptimized = new PacPicBankImporter().importFrom(jsonPath);

        assertEquals(AmosBank.Type.PACPIC, optimized.type());

        // Optimized must not be larger than unoptimized
        assertTrue(optimized.picData().length <= unoptimized.picData().length,
                "Optimized picData (" + optimized.picData().length + ") should be ≤ unoptimized ("
                + unoptimized.picData().length + ")");

        // Must decode to same dimensions
        var origPixels = PacPicDecoder.decompress(original.picData());
        var optPixels = PacPicDecoder.decompress(optimized.picData());
        assertEquals(origPixels.length, optPixels.length, "height");
        assertEquals(origPixels[0].length, optPixels[0].length, "width");
    }

    private record IlbmFile(Path iffPath, int planes) {
    }

    ;


    @Test
    void paletteOptimizerSavingsFromIlbm() throws Exception {
        System.out.println("| file                                          | width | height | colours | baseline | optimized | saving           | time");
        System.out.println("+-----------------------------------------------+-------+--------+---------+----------+-----------+------------------+------");
        for (var f : new IlbmFile[]{
                new IlbmFile(Path.of("src/test/resources/DefenderOfTheCrown2_Romantic_Fireplace.iff"), 5),
                new IlbmFile(Path.of("src/test/resources/DeviousDesigns_Level01.iff"), 4),
                new IlbmFile(Path.of("src/test/resources/DeviousDesigns_Level16.iff"), 4),
                new IlbmFile(Path.of("src/test/resources/Spherical.iff"), 4)
        }) {
            var image = IndexedPngWriter.readPixels(f.iffPath);
            int numColors = 1 << f.planes;

            int baseline = PacPicEncoder.compress(image.pixels(), 0, 0, f.planes).length;
            var t = System.currentTimeMillis();
            var result = PacPicPaletteOptimizer.optimize(image.pixels(), numColors, 0, 0, f.planes);
            t = System.currentTimeMillis() - t;

            int saving = baseline - result.compressedSize();
            double pct = 100.0 * saving / baseline;

            System.out.printf("| %-45s | %-5d | %-6d | %-7d | %-8d | %-9d | %-8d (%-4.1f%%) | %.2fs%n",
                    f.iffPath.getFileName(), image.width(), image.height(), numColors, baseline, result.compressedSize(), saving, pct, t / 1000.0);

            assertTrue(result.compressedSize() <= baseline,
                    "Optimizer must not produce larger output than baseline");
        }
    }

    @Test
    void importFromJsonReconstructsPacPicBank(@TempDir Path tmp) throws Exception {
        var original = PacPicBankReader.read(SPACK_ABK);
        var jsonPath = tmp.resolve("spack.json");

        new PacPicBankExporter().export(original, jsonPath);

        var imported = new PacPicBankImporter().importFrom(jsonPath);

        assertEquals(AmosBank.Type.PACPIC, imported.type());
        assertEquals(original.bankNumber(), imported.bankNumber());
        assertEquals(original.chipRam(), imported.chipRam());
        assertEquals(original.isSpack(), imported.isSpack());
        assertNotNull(imported.screenHeader());

        // Structural checks: importer re-compresses, so bytes may differ.
        var exp = PacPicDecoder.decompress(original.picData());
        var act = PacPicDecoder.decompress(imported.picData());
        assertEquals(exp.length, act.length, "height");
        assertEquals(exp[0].length, act[0].length, "width");
    }
}


