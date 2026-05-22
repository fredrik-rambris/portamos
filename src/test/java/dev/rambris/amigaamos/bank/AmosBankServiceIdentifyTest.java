/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AmosBankServiceIdentifyTest {

    // -------------------------------------------------------------------------
    // Binary .Abk files → AmosBank subclass
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource({
            "Sprites.Abk,             SpriteBank",
            "Icons.Abk,               SpriteBank",
            "Samples.abk,             SampleBank",
            "Music.abk,               MusicBank",
            "Amal.Abk,                AmalBank",
            "menu.abk,                MenuBank",
            "Data.Menu,               MenuBank",
            "Spack.Abk,               PacPicBank",
            "Resource_Bank_Maker.Abk, ResourceBank",
            "Work.Abk,                RawBank",
            "Data.Abk,                RawBank",
            "ChipWork.Abk,            RawBank",
            "ChipData.Abk,            RawBank",
    })
    void identifyAbk(String filename, String expectedSimpleName) {
        var result = AmosBankService.identify(Path.of("src/test/resources", filename));
        assertNotNull(result, filename);
        assertEquals(expectedSimpleName, result.getSimpleName(), filename);
    }

    // -------------------------------------------------------------------------
    // Non-bank files → null
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "{0} → null")
    @ValueSource(strings = {"Numbers.AMOS", "Numbers.Asc", "stereo.8svx"})
    void identifyNonBank_returnsNull(String filename) {
        assertNull(AmosBankService.identify(Path.of("src/test/resources", filename)));
    }

    // -------------------------------------------------------------------------
    // Exported JSON files → AmosBankDto subclass
    // -------------------------------------------------------------------------

    @Test
    void identifyJson_sprites(@TempDir Path tmp) throws Exception {
        assertEquals(SpriteBankDto.class, identify("Sprites.Abk", tmp));
    }

    @Test
    void identifyJson_icons(@TempDir Path tmp) throws Exception {
        assertEquals(SpriteBankDto.class, identify("Icons.Abk", tmp));
    }

    @Test
    void identifyJson_samples(@TempDir Path tmp) throws Exception {
        assertEquals(SampleBankDto.class, identify("Samples.abk", tmp));
    }

    @Test
    void identifyJson_music(@TempDir Path tmp) throws Exception {
        assertEquals(MusicBankDto.class, identify("Music.abk", tmp));
    }

    @Test
    void identifyJson_amal(@TempDir Path tmp) throws Exception {
        assertEquals(AmalBankDto.class, identify("Amal.Abk", tmp));
    }

    @Test
    void identifyJson_raw(@TempDir Path tmp) throws Exception {
        assertEquals(RawBankDto.class, identify("Work.Abk", tmp));
    }

    // -------------------------------------------------------------------------

    private static Class<?> identify(String abkFilename, Path outDir) throws Exception {
        var service = new AmosBankService();
        var bank = service.readBank(Path.of("src/test/resources", abkFilename));
        var jsonPath = outDir.resolve("bank.json");
        service.exportBank(bank, jsonPath);
        return AmosBankService.identify(jsonPath);
    }
}
