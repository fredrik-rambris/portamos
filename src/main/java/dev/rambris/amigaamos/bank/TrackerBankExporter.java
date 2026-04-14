/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void export(TrackerBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        Files.write(outDir.resolve(MOD_FILE), bank.modData());
        System.out.printf("Written %s (%d bytes)%n", outDir.resolve(MOD_FILE), bank.modData().length);

        var root = JSON.createObjectNode();
        root.put("type", "Tracker");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());
        root.put("modFile", MOD_FILE);

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s%n", dest);
    }
}
