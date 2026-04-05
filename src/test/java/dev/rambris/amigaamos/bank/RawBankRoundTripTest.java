package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class RawBankRoundTripTest {

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Work.Abk,    WORK, false",
            "ChipWork.Abk, WORK, true",
            "Data.Abk,    DATA, false",
            "ChipData.Abk, DATA, true",
    })
    void readTestFile(String filename, String expectedType, boolean expectedChip) throws Exception {
        var path = Path.of("src/test/resources", filename);
        var bank = RawBankReader.read(path);

        assertEquals(AmosBank.Type.valueOf(expectedType), bank.type(), "type");
        assertEquals(expectedChip, bank.chipRam(), "chipRam");
        assertEquals(400, payloadOf(bank).length, "payload size");

        // All payload bytes must be 0xAA (%10101010)
        for (byte b : payloadOf(bank)) {
            assertEquals((byte) 0xAA, b, "payload byte");
        }
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Work.Abk",
            "ChipWork.Abk",
            "Data.Abk",
            "ChipData.Abk",
    })
    void writeRoundTrip(String filename, @TempDir Path tmp) throws Exception {
        var original = Path.of("src/test/resources", filename);
        var bank = RawBankReader.read(original);

        Path written = tmp.resolve(filename);
        new RawBankWriter().write(bank, written);

        var readback = RawBankReader.read(written);
        assertEquals(bank.type(),       readback.type(),       "type");
        assertEquals(bank.bankNumber(), readback.bankNumber(), "bankNumber");
        assertEquals(bank.chipRam(),    readback.chipRam(),    "chipRam");
        assertArrayEquals(payloadOf(bank), payloadOf(readback), "payload");
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource({
            "Work.Abk",
            "ChipWork.Abk",
            "Data.Abk",
            "ChipData.Abk",
    })
    void importExportRoundTrip(String filename, @TempDir Path tmp) throws Exception {
        var original = Path.of("src/test/resources", filename);
        var bank = RawBankReader.read(original);

        // Export
        Path dataPath = tmp.resolve("payload.bin");
        new RawBankExporter().export(bank, dataPath);

        Path jsonPath = tmp.resolve("payload.bin.json");
        assertTrue(jsonPath.toFile().exists(), "JSON sidecar must exist");

        // Import
        AmosBank imported = new RawBankImporter().importFrom(jsonPath);
        assertEquals(bank.type(),       imported.type(),       "type");
        assertEquals(bank.bankNumber(), imported.bankNumber(), "bankNumber");
        assertEquals(bank.chipRam(),    imported.chipRam(),    "chipRam");
        assertArrayEquals(payloadOf(bank), payloadOf(imported), "payload");
    }

    @Test
    void importResolvesDataFileRelativeToJson(@TempDir Path tmp) throws Exception {
        // Export to one name, then manually edit JSON to reference a renamed data file
        var original = Path.of("src/test/resources/Work.Abk");
        var bank = RawBankReader.read(original);

        Path dataPath = tmp.resolve("original.bin");
        new RawBankExporter().export(bank, dataPath);

        // Rename the data file
        Path renamedData = tmp.resolve("renamed.dat");
        dataPath.toFile().renameTo(renamedData.toFile());

        // Write a new JSON pointing to the renamed file
        Path newJson = tmp.resolve("work.json");
        java.nio.file.Files.writeString(newJson, """
                {
                  "type"       : "WORK",
                  "bankNumber" : %d,
                  "chipRam"    : %b,
                  "dataFile"   : "renamed.dat"
                }
                """.formatted(bank.bankNumber() & 0xFFFF, bank.chipRam()),
                java.nio.charset.StandardCharsets.UTF_8);

        AmosBank imported = new RawBankImporter().importFrom(newJson);
        assertEquals(AmosBank.Type.WORK, imported.type());
        assertArrayEquals(payloadOf(bank), payloadOf(imported));
    }

    // -------------------------------------------------------------------------

    private static byte[] payloadOf(AmosBank bank) {
        if (!(bank instanceof RawBank rb)) throw new AssertionError("unexpected bank type: " + bank.type());
        return rb.data();
    }
}
