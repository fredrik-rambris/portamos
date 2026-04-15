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

class MusicBankTest {

    private static final Path MUSIC_ABK = Path.of("src/test/resources/Music.abk");

    // -------------------------------------------------------------------------
    // Reader
    // -------------------------------------------------------------------------

    @Test
    void read_viaGenericDispatch_returnsMusicBank() throws Exception {
        var bank = AmosBank.read(MUSIC_ABK);
        assertInstanceOf(MusicBank.class, bank);
        assertEquals(AmosBank.Type.MUSIC, bank.type());
    }

    @Test
    void read_instrumentCount_is7() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertEquals(7, bank.instruments().size());
    }

    @Test
    void read_songCount_is1() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertEquals(1, bank.songs().size());
    }

    @Test
    void read_patternCount_is27() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertEquals(27, bank.patterns().size());
    }

    @Test
    void read_chipRam_isTrue() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertTrue(bank.chipRam());
    }

    @Test
    void read_instrument0_hasName() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertFalse(bank.instruments().get(0).name().isBlank());
    }

    @Test
    void read_instrument0_hasSampleData() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertTrue(bank.instruments().get(0).sampleData().length > 0);
    }

    @Test
    void read_song0_has4voices() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertEquals(4, bank.songs().get(0).sequence().size());
    }

    @Test
    void read_pattern0_has4voices() throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        assertEquals(4, bank.patterns().get(0).voices().size());
    }

    // -------------------------------------------------------------------------
    // Writer binary round-trip
    // -------------------------------------------------------------------------

    @Test
    void writer_binaryRoundTrip_isIdentical() throws Exception {
        var original = Files.readAllBytes(MUSIC_ABK);
        var bank     = MusicBankReader.read(MUSIC_ABK);
        var rewritten = bank.writer().toBytes(bank);
        assertArrayEquals(original, rewritten);
    }

    // -------------------------------------------------------------------------
    // Exporter + Importer round-trip
    // -------------------------------------------------------------------------

    @Test
    void exportImport_wav_roundTrip_binaryIdentical(@TempDir Path outDir) throws Exception {
        var original = Files.readAllBytes(MUSIC_ABK);
        var bank     = MusicBankReader.read(MUSIC_ABK);

        new MusicBankExporter().export(bank, outDir);
        assertTrue(Files.exists(outDir.resolve("bank.json")));
        assertTrue(Files.exists(outDir.resolve("instrument_000.wav")));

        var imported  = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        var rewritten = imported.writer().toBytes(imported);
        assertArrayEquals(original, rewritten);
    }

    @Test
    void exportImport_svx8_roundTrip_binaryIdentical(@TempDir Path outDir) throws Exception {
        var original = Files.readAllBytes(MUSIC_ABK);
        var bank     = MusicBankReader.read(MUSIC_ABK);

        new MusicBankExporter().export(bank, outDir, true);
        assertTrue(Files.exists(outDir.resolve("bank.json")));
        assertTrue(Files.exists(outDir.resolve("instrument_000.8svx")));

        var imported  = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        var rewritten = imported.writer().toBytes(imported);
        assertArrayEquals(original, rewritten);
    }

    @Test
    void exportImport_preservesInstrumentCount(@TempDir Path outDir) throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        new MusicBankExporter().export(bank, outDir);
        var imported = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        assertEquals(bank.instruments().size(), imported.instruments().size());
    }

    @Test
    void exportImport_preservesSongAndPatternCounts(@TempDir Path outDir) throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        new MusicBankExporter().export(bank, outDir);
        var imported = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        assertEquals(bank.songs().size(),    imported.songs().size());
        assertEquals(bank.patterns().size(), imported.patterns().size());
    }

    @Test
    void exportImport_preservesInstrument0Name(@TempDir Path outDir) throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        new MusicBankExporter().export(bank, outDir);
        var imported = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        assertEquals(bank.instruments().get(0).name(), imported.instruments().get(0).name());
    }

    @Test
    void exportImport_preservesInstrument0SampleData(@TempDir Path outDir) throws Exception {
        var bank = MusicBankReader.read(MUSIC_ABK);
        new MusicBankExporter().export(bank, outDir);
        var imported = new MusicBankImporter().importFrom(outDir.resolve("bank.json"));
        assertArrayEquals(bank.instruments().get(0).sampleData(),
                imported.instruments().get(0).sampleData());
    }
}
