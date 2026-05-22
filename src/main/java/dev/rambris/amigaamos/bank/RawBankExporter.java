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
 * Exports a {@link RawBank} to a JSON metadata file and a sibling data file.
 *
 * <p>Usage: call {@link #export(AmosBank, Path)} with the desired JSON output path.
 * The raw payload is written to a sibling file using the JSON file's stem with {@code .bin}.
 *
 * <p>Example — given {@code jsonPath = "path/to/mydata.json"}:
 * <pre>
 *   path/to/mydata.json  ← metadata
 *   path/to/mydata.bin   ← raw payload bytes (no AmBk header)
 * </pre>
 *
 * <p>JSON format:
 * <pre>
 * {
 *   "type":       "Work" | "Data",
 *   "bankNumber": 10,
 *   "chipRam":    false,
 *   "dataFile":   "mydata.bin"   ← filename only (no directory component)
 * }
 * </pre>
 *
 * <p>The {@code dataFile} value is the file name only (no directory path), so that
 * the exported bundle is relocatable.  {@link RawBankImporter} resolves it relative
 * to the JSON file's parent directory.
 */
public class RawBankExporter {


    /**
     * Exports {@code bank} to {@code jsonPath} (metadata) and a sibling {@code stem.bin} (payload).
     *
     * @param bank     the Work or Data bank to export
     * @param jsonPath destination JSON metadata file
     * @throws IllegalArgumentException if {@code bank} is not a {@link RawBank}
     */
    public void export(AmosBank bank, Path jsonPath) throws IOException {
        if (!(bank instanceof RawBank rb))
            throw new IllegalArgumentException("Not a Work or Data bank, got: " + bank.getClass().getSimpleName());

        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var dataFilename = stem + ".bin";
        Files.write(dir.resolve(dataFilename), rb.data());

        var typeStr = bank.type() == AmosBank.Type.WORK ? RawBankDto.TYPE_WORK : RawBankDto.TYPE_DATA;
        var dto = new RawBankDto(
                typeStr,
                bank.bankNumber() & 0xFFFF,
                bank.chipRam(),
                dataFilename);

        JSON.writeValue(jsonPath.toFile(), dto);
    }
}
