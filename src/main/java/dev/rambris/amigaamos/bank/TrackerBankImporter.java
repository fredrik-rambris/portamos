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
 * Imports a {@link TrackerBank} from a JSON metadata file previously produced by
 * {@link TrackerBankExporter}.
 */
public class TrackerBankImporter {


    public TrackerBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), TrackerBankDto.class);

        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 3);
        var chipRam = dto.chipRam() != null && dto.chipRam();
        var modFile = dto.modFile() != null ? dto.modFile() : "track.mod";

        var modData = Files.readAllBytes(jsonPath.resolveSibling(modFile));

        return new TrackerBank(bankNumber, chipRam, modData);
    }
}
