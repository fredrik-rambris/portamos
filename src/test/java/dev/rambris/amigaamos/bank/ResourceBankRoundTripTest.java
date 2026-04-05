package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResourceBankRoundTripTest {

    private static final Path DEFAULT_BANK = Path.of(
            "reference/AMOSProfessional/AMOS/APSystem/AMOSPro_Default_Resource.Abk");

    @Test
    void roundTrip(@TempDir Path tmp) throws Exception {
        var original = (ResourceBank) AmosBank.read(DEFAULT_BANK);

        Path written = tmp.resolve("roundtrip.Abk");
        new ResourceBankWriter().write(original, written);

        var readback = (ResourceBank) AmosBank.read(written);

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
        // Check element images have the same geometry
        for (int i = 0; i < original.elements().size(); i++) {
            var oel = original.elements().get(i);
            var rel = readback.elements().get(i);
            assertEquals(oel.name(), rel.name(), "element[" + i + "].name");
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
