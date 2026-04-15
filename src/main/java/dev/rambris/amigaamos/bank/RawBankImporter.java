/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Imports a Work or Data bank from the given JSON metadata file.
     *
     * @param jsonPath path to the {@code .json} metadata file
     * @return a {@link RawBank} with type {@code WORK} or {@code DATA}
     * @throws IOException              if any file cannot be read
     * @throws IllegalArgumentException if the {@code type} field is not {@code WORK} or {@code DATA}
     */
    public RawBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var typeName     = root.path("type").asText();
        short bankNumber = (short) root.path("bankNumber").asInt(1);
        boolean chipRam  = root.path("chipRam").asBoolean(false);
        var dataFile     = root.path("dataFile").asText();

        var dataPath = jsonPath.resolveSibling(dataFile);
        var data     = Files.readAllBytes(dataPath);

        var type = switch (typeName) {
            case "WORK" -> AmosBank.Type.WORK;
            case "DATA" -> AmosBank.Type.DATA;
            default -> throw new IllegalArgumentException("Unknown bank type: " + typeName);
        };
        return new RawBank(type, bankNumber, chipRam, data);
    }
}
