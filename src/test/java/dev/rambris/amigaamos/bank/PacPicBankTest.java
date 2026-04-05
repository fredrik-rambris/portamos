package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

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
    void exportWritesPngAndSidecarJson(@TempDir Path tmp) throws Exception {
        var bank = PacPicBankReader.read(SPACK_ABK);
        var pngPath = tmp.resolve("spack.png");

        new PacPicBankExporter().export(bank, pngPath);

        assertTrue(pngPath.toFile().exists(), "png file");
        assertTrue(Path.of(pngPath.toString() + ".json").toFile().exists(), "sidecar json");
    }

    @Test
    void importFromJsonReconstructsPacPicBank(@TempDir Path tmp) throws Exception {
        var original = PacPicBankReader.read(SPACK_ABK);
        var pngPath = tmp.resolve("spack.png");

        new PacPicBankExporter().export(original, pngPath);
        var jsonPath = Path.of(pngPath.toString() + ".json");

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


