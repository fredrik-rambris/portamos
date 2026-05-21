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

    private static final String MOD_FILE = "track.mod";


    public void export(TrackerBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        Files.write(outDir.resolve(MOD_FILE), bank.modData());
        System.out.printf("Written %s (%d bytes)%n", outDir.resolve(MOD_FILE), bank.modData().length);

        var dto = new TrackerBankDto(TrackerBankDto.TYPE, bank.bankNumber() & 0xFFFF, bank.chipRam(), MOD_FILE);

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), dto);
        System.out.printf("Written %s%n", dest);
    }
}
