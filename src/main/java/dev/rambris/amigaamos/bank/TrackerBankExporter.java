/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.TrackerBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link TrackerBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code bank.json} — metadata: bank number, chip RAM flag, and MOD filename.</li>
 *   <li>{@code track.mod} — the ProTracker MOD file extracted from the bank payload.</li>
 * </ul>
 */
public class TrackerBankExporter {

    public void export(TrackerBank bank, Path jsonPath) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var modFilename = stem + ".mod";
        Files.write(dir.resolve(modFilename), bank.modData());
        System.out.printf("Written %s (%d bytes)%n", dir.resolve(modFilename), bank.modData().length);

        var dto = new TrackerBankDto(TrackerBankDto.TYPE, bank.bankNumber() & 0xFFFF, bank.chipRam(), modFilename);

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s%n", jsonPath);
    }
}
