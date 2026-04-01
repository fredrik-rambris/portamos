package dev.rambris.amos.bank;

import java.util.List;

/**
 * In-memory model of a parsed AMOS Professional Resource Bank.
 *
 * <p>The images section contains a flat list of <em>elements</em>.  Each element is one
 * entry in the binary offset table and corresponds to one of two formats:
 * <ul>
 *   <li><b>Simple image</b> (Image_NN in the spec): entry starts with the Pac.Pic magic
 *       {@code 0x06071963}.  One image, {@code type} and {@code name} are null.</li>
 *   <li><b>Named element</b> (Res_NN in the spec): entry starts with an 8-byte ASCII name,
 *       followed by a 2-byte image count, 2-byte {@code 0xABCD} marker, then one or more
 *       consecutive Pac.Pic images.  {@code type} is inferred from the count:
 *       1 → SINGLE, 3 → LINE, 9 → BOX.</li>
 * </ul>
 */
public record ResourceBank (
        short bankNumber,
        boolean chipRam,
        int screenMode,
        int[] palette,        // 32 entries, 12-bit Amiga colour each
        String imagePath,     // source IFF path stored in the bank header
        List<Element> elements,
        List<String> texts,
        List<String> programs // DBL Interface programs (.amui)
) implements AmosBank {

    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    /**
     * One entry in the image offset table.
     *
     * <p>For simple images {@code name} and {@code type} are {@code null} and
     * {@code images} has exactly one entry.
     * For named elements they are set and {@code images} may have 1, 3, or 9 entries.
     */
    public record Element(
            String name,          // null for simple images; ≤8 chars for named elements
            String type,          // null | "SINGLE" | "LINE" | "BOX"
            List<Image> images
    ) {}

    /** One Pac.Pic image within an element. */
    public record Image(
            int x,                // source X in pixels
            int y,                // source Y in pixels
            int width,            // width in pixels
            int height,           // height in pixels  (height_in_y × height_in_lines)
            int planes,           // number of bitplanes
            byte[] data           // raw Pac.Pic bytes, starting with 0x06071963
    ) {}

    public int getNumCols() {
        return elements.stream().flatMap(e -> e.images.stream()).mapToInt(i -> 1<<i.planes).max().orElse(palette.length);
    }
}
