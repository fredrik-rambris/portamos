/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Round-trip test: read .Abk → export to directory → import back → write .Abk → read again.
 */
class ResourceBankImportExportTest {

    private static final Path DEFAULT_BANK = Path.of(
            "reference/AMOSProfessional/AMOS/APSystem/AMOSPro_Default_Resource.Abk");

    @Test
    void importExportRoundTrip(@TempDir Path tmp) throws Exception {
        // 1. Read the original bank
        var original = (ResourceBank) AmosBank.read(DEFAULT_BANK);

        // 2. Export to a directory
        Path exportDir = tmp.resolve("exported");
        new ResourceBankExporter().export(original, exportDir);

        // 3. Import back from the bank.json file in the exported directory
        var imported = new ResourceBankImporter().importFrom(exportDir.resolve("bank.json"));

        // 4. Write the imported bank to a file and read it back
        Path written = tmp.resolve("reimported.Abk");
        new ResourceBankWriter().write(imported, written);
        var readback = (ResourceBank) AmosBank.read(written);

        // Structural checks
        assertEquals(original.bankNumber(),  readback.bankNumber(),  "bankNumber");
        assertEquals(original.chipRam(),     readback.chipRam(),     "chipRam");
        assertEquals(original.screenMode(),  readback.screenMode(),  "screenMode");
        assertEquals(original.imagePath(),   readback.imagePath(),   "imagePath");
        assertEquals(original.elements().size(), readback.elements().size(), "element count");
        assertEquals(original.texts().size(),    readback.texts().size(),    "text count");
        assertEquals(original.programs().size(), readback.programs().size(), "program count");

        for (int i = 0; i < original.texts().size(); i++) {
            assertEquals(original.texts().get(i), readback.texts().get(i), "text[" + i + "]");
        }
        for (int i = 0; i < original.programs().size(); i++) {
            assertEquals(original.programs().get(i), readback.programs().get(i), "program[" + i + "]");
        }
        for (int i = 0; i < original.elements().size(); i++) {
            var oel = original.elements().get(i);
            var rel = readback.elements().get(i);
            assertEquals(oel.name(), rel.name(), "element[" + i + "].name");
            assertEquals(oel.type(), rel.type(), "element[" + i + "].type");
            assertEquals(oel.images().size(), rel.images().size(), "element[" + i + "].image count");
            for (int k = 0; k < oel.images().size(); k++) {
                var oi = oel.images().get(k);
                var ri = rel.images().get(k);
                assertEquals(oi.x(),      ri.x(),      "element[" + i + "].image[" + k + "].x");
                assertEquals(oi.y(),      ri.y(),      "element[" + i + "].image[" + k + "].y");
                assertEquals(oi.width(),  ri.width(),  "element[" + i + "].image[" + k + "].width");
                assertEquals(oi.height(), ri.height(), "element[" + i + "].image[" + k + "].height");
                assertEquals(oi.planes(), ri.planes(), "element[" + i + "].image[" + k + "].planes");
            }
        }
    }
}
