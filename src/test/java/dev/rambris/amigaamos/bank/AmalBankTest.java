/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AmalBankTest {

    private static final Path AMAL_ABK = Path.of("src/test/resources/Amal.Abk");

    // -------------------------------------------------------------------------
    // Reader
    // -------------------------------------------------------------------------

    @Test
    void read_viaGenericDispatch_returnsAmalBank() throws Exception {
        var bank = AmosBank.read(AMAL_ABK);
        assertInstanceOf(AmalBank.class, bank);
        assertEquals(AmosBank.Type.AMAL, bank.type());
    }

    @Test
    void read_bankNumber_is4() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertEquals(4, bank.bankNumber() & 0xFFFF);
    }

    @Test
    void read_chipRam_isFalse() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertFalse(bank.chipRam());
    }

    @Test
    void read_movements_count() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertEquals(48, bank.movements().size());
    }

    @Test
    void read_movement0_name() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertEquals("Move 1", bank.movements().get(0).name());
    }

    @Test
    void read_movement0_hasXMove() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        var mov = bank.movements().get(0);
        assertNotNull(mov.xMove(), "movement 0 should have X data");
        assertEquals(1, mov.xMove().speed());
    }

    @Test
    void read_movement0_xMove_instructionCount() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        var instructions = bank.movements().get(0).xMove().instructions();
        assertEquals(345, instructions.size());
    }

    @Test
    void read_movement0_xMove_firstInstructionsAreWaitThenDelta() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        var instructions = bank.movements().get(0).xMove().instructions();
        assertInstanceOf(AmalBank.Instruction.Wait.class, instructions.get(0));
        assertEquals(11, ((AmalBank.Instruction.Wait) instructions.get(0)).ticks());
        assertInstanceOf(AmalBank.Instruction.Delta.class, instructions.get(1));
        assertEquals(-1, ((AmalBank.Instruction.Delta) instructions.get(1)).pixels());
    }

    @Test
    void read_movement0_yMove_isNull() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertNull(bank.movements().get(0).yMove(), "movement 0 Y offset points outside moves area");
    }

    @Test
    void read_movements1to47_areEmpty() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        for (int i = 1; i < bank.movements().size(); i++) {
            assertTrue(bank.movements().get(i).isEmpty(),
                    "movement[" + i + "] should be empty");
        }
    }

    @Test
    void read_programs_count() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        assertEquals(63, bank.programs().size());
    }

    @Test
    void read_program0_containsAmalSource() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        String prog = bank.programs().get(0);
        assertFalse(prog.isEmpty(), "program[0] should have content");
        assertTrue(prog.contains("Begin:"), "program[0] should contain AMAL label 'Begin:'");
        assertTrue(prog.contains("~"), "program[0] should use ~ as line separator");
    }

    @Test
    void read_programs1to62_areEmpty() throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        for (int i = 1; i < bank.programs().size(); i++) {
            assertTrue(bank.programs().get(i).isEmpty(),
                    "program[" + i + "] should be empty");
        }
    }

    // -------------------------------------------------------------------------
    // Writer round-trip
    // -------------------------------------------------------------------------

    @Test
    void writer_roundTrip_preservesMovements(@TempDir Path tmp) throws Exception {
        var original = AmalBankReader.read(AMAL_ABK);
        byte[] bytes = original.writer().toBytes(original);
        var reread = AmalBankReader.read(bytes);

        assertEquals(original.movements().size(), reread.movements().size());
        var origMov = original.movements().get(0);
        var rtMov   = reread.movements().get(0);
        assertEquals(origMov.name(), rtMov.name());
        assertEquals(origMov.xMove().speed(), rtMov.xMove().speed());
        assertEquals(origMov.xMove().instructions().size(), rtMov.xMove().instructions().size());
        assertEquals(origMov.xMove().instructions(), rtMov.xMove().instructions());
    }

    @Test
    void writer_roundTrip_preservesPrograms(@TempDir Path tmp) throws Exception {
        var original = AmalBankReader.read(AMAL_ABK);
        byte[] bytes = original.writer().toBytes(original);
        var reread = AmalBankReader.read(bytes);

        assertEquals(original.programs().size(), reread.programs().size());
        assertEquals(original.programs().get(0), reread.programs().get(0));
    }

    // -------------------------------------------------------------------------
    // Importer round-trip
    // -------------------------------------------------------------------------

    @Test
    void importer_roundTrip_preservesProgram(@TempDir Path outDir) throws Exception {
        var original = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(original, outDir);
        var imported = new AmalBankImporter().importFrom(outDir.resolve("bank.json"));
        assertEquals(original.programs().get(0), imported.programs().get(0));
    }

    @Test
    void importer_roundTrip_preservesMovements(@TempDir Path outDir) throws Exception {
        var original = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(original, outDir);
        var imported = new AmalBankImporter().importFrom(outDir.resolve("bank.json"));
        var origMov = original.movements().get(0);
        var impMov  = imported.movements().get(0);
        assertEquals(origMov.name(), impMov.name());
        assertEquals(origMov.xMove().speed(), impMov.xMove().speed());
        assertEquals(origMov.xMove().instructions(), impMov.xMove().instructions());
    }

    @Test
    void importer_thenWriter_fullRoundTrip(@TempDir Path outDir) throws Exception {
        var original = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(original, outDir);
        var imported = new AmalBankImporter().importFrom(outDir.resolve("bank.json"));
        byte[] bytes = imported.writer().toBytes(imported);
        var reread = AmalBankReader.read(bytes);
        assertEquals(original.programs().get(0), reread.programs().get(0));
        assertEquals(original.movements().get(0).xMove().instructions(),
                     reread.movements().get(0).xMove().instructions());
    }

    // -------------------------------------------------------------------------
    // Exporter
    // -------------------------------------------------------------------------

    @Test
    void export_createsExpectedFiles(@TempDir Path outDir) throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(bank, outDir);

        assertTrue(Files.exists(outDir.resolve("bank.json")));
        assertTrue(Files.exists(outDir.resolve("movement_000.json")));
        assertTrue(Files.exists(outDir.resolve("program_000.amal")));
    }

    @Test
    void export_program_tildeSeparatorReplacedWithNewline(@TempDir Path outDir) throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(bank, outDir);

        String text = Files.readString(outDir.resolve("program_000.amal"));
        assertFalse(text.contains("~"), "exported program should not contain ~");
        assertTrue(text.contains("\n"), "exported program should use newlines");
        assertTrue(text.contains("Begin:"), "program content should be preserved");
    }

    @Test
    void export_movement_json_hasCorrectStructure(@TempDir Path outDir) throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(bank, outDir);

        String json = Files.readString(outDir.resolve("movement_000.json"));
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"speed\""));
        assertTrue(json.contains("\"instructions\""));
        assertTrue(json.contains("\"wait\""));
        assertTrue(json.contains("\"delta\""));
    }

    @Test
    void export_bankJson_hasCorrectStructure(@TempDir Path outDir) throws Exception {
        var bank = AmalBankReader.read(AMAL_ABK);
        new AmalBankExporter().export(bank, outDir);

        String json = Files.readString(outDir.resolve("bank.json"));
        assertTrue(json.contains("\"type\" : \"Amal\""));
        assertTrue(json.contains("\"bankNumber\" : 4"));
        assertTrue(json.contains("\"movements\""));
        assertTrue(json.contains("\"programs\""));
    }
}
