/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.BankSetDto;

import java.io.IOException;
import java.util.ArrayList;

import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link BankSet} from a JSON index file produced by {@link BankSetExporter}.
 *
 * <p>Each filename listed in the JSON is resolved relative to the JSON file's parent directory
 * and read as a binary {@code .Abk} file.
 */
public class BankSetImporter {

    public BankSet importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), BankSetDto.class);
        var dir = jsonPath.toAbsolutePath().getParent();

        var banks = new ArrayList<AmosBank>(dto.banks().size());
        for (var filename : dto.banks()) {
            banks.add(AmosBank.read(dir.resolve(filename)));
        }

        return new BankSet(banks);
    }
}
