/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.BankSetDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link BankSet} to a JSON index file and a set of sibling {@code .Abk} files.
 *
 * <p>Each bank is written as {@code <stem>-<index>-<banktype>-<bankno>.abk}, for example:
 * <pre>
 *   explosion-1-data-9.abk
 *   explosion-2-samples-5.abk
 * </pre>
 * where {@code <index>} is 1-based, {@code <banktype>} is the bank type name in lower case,
 * and {@code <bankno>} is the bank's slot number from the AmBk header.
 *
 * <p>A JSON index file is written to {@code jsonPath} listing all generated filenames.
 */
public class BankSetExporter {

    public void export(BankSet bankSet, Path jsonPath) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var filenames = new ArrayList<String>(bankSet.banks().size());

        for (int i = 0; i < bankSet.banks().size(); i++) {
            var bank = bankSet.banks().get(i);
            var filename = stem + "-" + (i + 1) + "-"
                    + bank.type().toString().toLowerCase() + "-"
                    + (bank.bankNumber() & 0xFFFF) + ".abk";
            filenames.add(filename);
            bank.writer().write(bank, dir.resolve(filename));
        }

        var dto = new BankSetDto(BankSetDto.TYPE, filenames);
        JSON.writeValue(jsonPath.toFile(), dto);
    }
}
