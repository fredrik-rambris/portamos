/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.RawBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link RawBank} from a JSON metadata file
 * previously produced by {@link RawBankExporter}.
 *
 * <p>Usage: call {@link #importFrom(Path)} with the path to the {@code .json} file.
 * The raw data file is resolved relative to the JSON file's parent directory using
 * the {@code dataFile} field in the JSON (filename only).
 *
 * <p>Example — given {@code jsonPath = "path/to/MyData.dat.json"} containing
 * {@code "dataFile": "MyNewData.foo"}:
 * <pre>
 *   data file resolved to: path/to/MyNewData.foo
 * </pre>
 */
public class RawBankImporter {


    /**
     * Imports a Work or Data bank from the given JSON metadata file.
     *
     * @param jsonPath path to the {@code .json} metadata file
     * @return a {@link RawBank} with type {@code WORK} or {@code DATA}
     * @throws IOException              if any file cannot be read
     * @throws IllegalArgumentException if the {@code type} field is not {@code WORK} or {@code DATA}
     */
    public RawBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), RawBankDto.class);

        var typeName = dto.type() != null ? dto.type().toUpperCase() : "";
        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 1);
        var chipRam = dto.chipRam() != null && dto.chipRam();
        var dataFile = dto.dataFile() != null ? dto.dataFile() : "";

        var data = Files.readAllBytes(jsonPath.resolveSibling(dataFile));

        var type = switch (typeName) {
            case "WORK" -> AmosBank.Type.WORK;
            case "DATA" -> AmosBank.Type.DATA;
            default -> throw new IllegalArgumentException("Unknown bank type: " + typeName);
        };
        return new RawBank(type, bankNumber, chipRam, data);
    }
}
