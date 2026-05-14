/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link TrackerBank} from a JSON metadata file previously produced by
 * {@link TrackerBankExporter}.
 */
public class TrackerBankImporter {


    public TrackerBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var bankNumber = (short) root.path("bankNumber").asInt(3);
        var chipRam    = root.path("chipRam").asBoolean(false);
        var modFile    = root.path("modFile").asText("track.mod");

        var modPath = jsonPath.resolveSibling(modFile);
        var modData = Files.readAllBytes(modPath);

        return new TrackerBank(bankNumber, chipRam, modData);
    }
}
