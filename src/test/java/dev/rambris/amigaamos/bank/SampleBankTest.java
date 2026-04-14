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

class SampleBankTest {

    private static final Path SAMPLES_ABK = Path.of("src/test/resources/Samples.abk");

    // -------------------------------------------------------------------------
    // Reader
    // -------------------------------------------------------------------------

    @Test
    void read_viaGenericDispatch_returnsSampleBank() throws Exception {
        var bank = AmosBank.read(SAMPLES_ABK);
        assertInstanceOf(SampleBank.class, bank);
        assertEquals(AmosBank.Type.SAMPLES, bank.type());
    }

    @Test
    void read_sampleCount_is6() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        assertEquals(6, bank.samples().size());
    }

    @Test
    void read_sample0_name() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        assertEquals("BanjoSyn", bank.samples().get(0).name());
    }

    @Test
    void read_sample0_frequency() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        assertEquals(8363, bank.samples().get(0).frequencyHz());
    }

    @Test
    void read_sample0_dataLength() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        assertEquals(6500, bank.samples().get(0).pcmData().length);
    }

    @Test
    void read_sample1_nameStripsGarbage() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        // Raw bytes are "Bouncy\x00\x01" — trailing garbage must be stripped
        assertEquals("Bouncy", bank.samples().get(1).name());
    }

    @Test
    void read_sample3_frequency_differs() throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        // LAAA is sampled at a different rate
        assertEquals(14563, bank.samples().get(3).frequencyHz());
    }

    // -------------------------------------------------------------------------
    // Writer round-trip
    // -------------------------------------------------------------------------

    @Test
    void writer_roundTrip_preservesSampleCount() throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        var reread   = SampleBankReader.read(original.writer().toBytes(original));
        assertEquals(original.samples().size(), reread.samples().size());
    }

    @Test
    void writer_roundTrip_preservesSample0() throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        var reread   = SampleBankReader.read(original.writer().toBytes(original));
        var orig = original.samples().get(0);
        var rt   = reread.samples().get(0);
        assertEquals(orig.name(), rt.name());
        assertEquals(orig.frequencyHz(), rt.frequencyHz());
        assertArrayEquals(orig.pcmData(), rt.pcmData());
    }

    @Test
    void writer_roundTrip_binaryIdentical() throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        var bytes    = original.writer().toBytes(original);
        var reread   = SampleBankReader.read(bytes);
        // Re-serialize and compare sizes
        assertArrayEquals(bytes, reread.writer().toBytes(reread));
    }

    // -------------------------------------------------------------------------
    // Exporter — WAV
    // -------------------------------------------------------------------------

    @Test
    void export_wav_createsExpectedFiles(@TempDir Path outDir) throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(bank, outDir);
        assertTrue(Files.exists(outDir.resolve("bank.json")));
        assertTrue(Files.exists(outDir.resolve("sample_000.wav")));
        assertTrue(Files.exists(outDir.resolve("sample_005.wav")));
    }

    @Test
    void export_wav_bankJsonHasCorrectStructure(@TempDir Path outDir) throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(bank, outDir);
        var json = Files.readString(outDir.resolve("bank.json"));
        assertTrue(json.contains("\"type\" : \"Samples\""));
        assertTrue(json.contains("\"samples\""));
        assertTrue(json.contains("\"frequencyHz\""));
        assertTrue(json.contains("sample_000.wav"));
    }

    // -------------------------------------------------------------------------
    // Exporter — 8SVX
    // -------------------------------------------------------------------------

    @Test
    void export_svx8_createsSvx8Files(@TempDir Path outDir) throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(bank, outDir, true);
        assertTrue(Files.exists(outDir.resolve("bank.json")));
        assertTrue(Files.exists(outDir.resolve("sample_000.8svx")));
    }

    @Test
    void export_svx8_bankJsonReferencesSvxFiles(@TempDir Path outDir) throws Exception {
        var bank = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(bank, outDir, true);
        var json = Files.readString(outDir.resolve("bank.json"));
        assertTrue(json.contains("sample_000.8svx"));
    }

    // -------------------------------------------------------------------------
    // Importer round-trip — WAV
    // -------------------------------------------------------------------------

    @Test
    void importer_wav_roundTrip_preservesSample0(@TempDir Path outDir) throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(original, outDir);
        var imported = new SampleBankImporter().importFrom(outDir.resolve("bank.json"));
        var orig = original.samples().get(0);
        var imp  = imported.samples().get(0);
        assertEquals(orig.name(), imp.name());
        assertEquals(orig.frequencyHz(), imp.frequencyHz());
        assertArrayEquals(orig.pcmData(), imp.pcmData());
    }

    @Test
    void importer_wav_thenWriter_fullRoundTrip(@TempDir Path outDir) throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(original, outDir);
        var imported = new SampleBankImporter().importFrom(outDir.resolve("bank.json"));
        var reread   = SampleBankReader.read(imported.writer().toBytes(imported));
        assertEquals(original.samples().get(0).name(), reread.samples().get(0).name());
        assertArrayEquals(original.samples().get(0).pcmData(), reread.samples().get(0).pcmData());
    }

    // -------------------------------------------------------------------------
    // Importer round-trip — 8SVX
    // -------------------------------------------------------------------------

    @Test
    void importer_svx8_roundTrip_preservesSample0(@TempDir Path outDir) throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(original, outDir, true);
        var imported = new SampleBankImporter().importFrom(outDir.resolve("bank.json"));
        var orig = original.samples().get(0);
        var imp  = imported.samples().get(0);
        assertEquals(orig.name(), imp.name());
        assertEquals(orig.frequencyHz(), imp.frequencyHz());
        assertArrayEquals(orig.pcmData(), imp.pcmData());
    }

    @Test
    void importer_svx8_thenWriter_fullRoundTrip(@TempDir Path outDir) throws Exception {
        var original = SampleBankReader.read(SAMPLES_ABK);
        new SampleBankExporter().export(original, outDir, true);
        var imported = new SampleBankImporter().importFrom(outDir.resolve("bank.json"));
        var reread   = SampleBankReader.read(imported.writer().toBytes(imported));
        assertArrayEquals(original.samples().get(0).pcmData(), reread.samples().get(0).pcmData());
    }
}
