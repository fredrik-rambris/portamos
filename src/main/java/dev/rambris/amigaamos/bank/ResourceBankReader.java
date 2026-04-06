package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AMOS Professional Resource Bank (.Abk) file.
 *
 * <p>After the binary is parsed into a flat list of {@link ResourceBank.Image} objects
 * (one per offset-table entry), a grouping pass converts consecutive images into
 * {@link ResourceBank.Element}s:
 * <ul>
 *   <li><b>BOX</b>: nine images whose (x,y) coordinates form a 3×3 grid with uniform tile size</li>
 *   <li><b>LINE</b>: three images in a horizontal or vertical run with uniform tile size</li>
 *   <li>otherwise: single image, {@code type} and {@code name} are {@code null}</li>
 * </ul>
 *
 * <p>File layout (big-endian):
 * <pre>
 *   [4]   magic "AmBk"
 *   [2]   bank number
 *   [2]   flags  (bit 1 = chip RAM)
 *   [4]   bank length in bytes
 *   [8]   bank name "Resource"
 *   ---   data_start (offset 20) ---
 *   [2]   BKCHUNKS (= 3 for resource bank)
 *   [4]   images offset  (relative to data_start; 0 = absent)
 *   [4]   texts offset   (relative to data_start; 0 = absent)
 *   [4]   DBL offset     (relative to data_start; 0 = absent)
 *   [4]   reserved 1
 *   [4]   reserved 2
 *   [4]   reserved 3
 *   ---   images section (at data_start + images_offset) ---
 *   [2]   n_entries
 *   [4×n] entry offsets (relative to images section start)
 *   [2]   n_colors
 *   [2]   screen_mode
 *   [64]  palette (32 × word)
 *   [2]   name_len
 *   [?]   name bytes (padded to even)
 *   ---   entry data; each entry is either: ---
 *     Image_NN: starts with Pac.Pic magic 0x06071963
 *     Res_NN  : [8] name + [2] count + [2] 0xABCD + count × Pac.Pic
 *   ---   texts section (at data_start + texts_offset) ---
 *   repeating: 0x00, length_byte, text_bytes; terminated by 0x00 0xFF
 *   ---   DBL section (at data_start + dbl_offset) ---
 *   [2]   n_programs
 *   [4×n] program offsets (relative to DBL section start)
 *   Each program: [2] length_including_itself, [length-2] program_bytes (with trailing NUL)
 * </pre>
 */
public class ResourceBankReader {

    // Offsets in this reader are relative to the AmBk payload start.
    private static final int DATA_START = 0;
    private static final int PAC_PIC_MAGIC = PacPicFormat.PK_MAGIC;

    public static ResourceBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static ResourceBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        if (hdr.type() != AmosBank.Type.RESOURCE) {
            throw new IOException("Expected \"" + AmosBank.Type.RESOURCE.identifier()
                    + "\" bank, got: \"" + hdr.typeName() + "\"");
        }

        var bankNumber = hdr.bankNumber();
        var chipRam = hdr.chipRam();
        var payload = hdr.payload();
        var buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);

        // ---- Resource bank sub-header ----
        buf.position(DATA_START);
        buf.getShort();          // BKCHUNKS — skip
        var imagesOffset = buf.getInt();
        var textsOffset = buf.getInt();
        var dblOffset = buf.getInt();
        // three reserved longs — buf is now at DATA_START+26

        // ---- Parse sections ----
        var imgs = new ImagesResult(List.of(), 0, new int[32], "");
        List<String> texts = List.of();
        List<String> programs = List.of();

        if (imagesOffset > 0) {
            var imagesEnd = payload.length;
            if (textsOffset > 0) imagesEnd = Math.min(imagesEnd, DATA_START + textsOffset);
            if (dblOffset > 0) imagesEnd = Math.min(imagesEnd, DATA_START + dblOffset);
            imgs = parseImages(buf, DATA_START + imagesOffset, imagesEnd);
        }
        if (textsOffset > 0) {
            buf.position(DATA_START + textsOffset);
            texts = parseTexts(buf);
        }
        if (dblOffset > 0) {
            buf.position(DATA_START + dblOffset);
            programs = parseDBL(buf);
        }

        return new ResourceBank(bankNumber, chipRam,
                imgs.screenMode(), imgs.palette(), imgs.imagePath(),
                imgs.elements(), texts, programs);
    }

    // -------------------------------------------------------------------------
    // Images section
    // -------------------------------------------------------------------------

    private record ImagesResult(List<ResourceBank.Element> elements,
                                int screenMode, int[] palette, String imagePath) {
    }

    private static ImagesResult parseImages(ByteBuffer buf, int sectionStart, int sectionEnd) {
        buf.position(sectionStart);

        int nEntries = buf.getShort() & 0xFFFF;
        int[] offsets = new int[nEntries];
        for (int i = 0; i < nEntries; i++) {
            offsets[i] = buf.getInt();
        }

        int nColors = buf.getShort() & 0xFFFF;
        int screenMode = buf.getShort() & 0xFFFF;
        int[] palette = new int[32];
        for (int i = 0; i < 32; i++) {
            palette[i] = buf.getShort() & 0xFFFF;
        }
        int nameLen = buf.getShort() & 0xFFFF;
        byte[] nameBuf = new byte[nameLen];
        buf.get(nameBuf);
        if (nameLen % 2 != 0) buf.get(); // pad to word boundary
        String imagePath = new String(nameBuf, StandardCharsets.ISO_8859_1);

        // Parse each entry as either a named Element or a flat list of Images.
        // Named elements (Res_NN) keep their explicit type; simple Pac.Pics become
        // temporary Element(null,null,[image]) wrappers that the grouper will merge.
        List<ResourceBank.Element> rawElements = new ArrayList<>(nEntries);
        for (int i = 0; i < nEntries; i++) {
            int entryStart = sectionStart + offsets[i];
            int entryEnd = (i + 1 < nEntries) ? sectionStart + offsets[i + 1] : sectionEnd;
            rawElements.add(parseEntry(buf, entryStart, entryEnd));
        }

        return new ImagesResult(groupElements(rawElements), screenMode, palette, imagePath);
    }

    /**
     * Parses one offset-table entry into an Element.
     *
     * <ul>
     *   <li>If the entry starts with {@code 0x06071963}: a simple Pac.Pic image wrapped in an
     *       untyped single-image element (will be merged by the grouper).</li>
     *   <li>Otherwise: a named Res_NN element with explicit name, count, and Pac.Pic images.</li>
     * </ul>
     */
    private static ResourceBank.Element parseEntry(ByteBuffer buf, int entryStart, int entryEnd) {
        buf.position(entryStart);
        int first4 = buf.getInt();

        if (first4 == PAC_PIC_MAGIC) {
            buf.position(entryStart);
            return new ResourceBank.Element(null, null,
                    List.of(readPacPic(buf, entryStart, entryEnd)));
        }

        // Named element: 8-byte name, 2-byte count, 2-byte ABCD, then count × Pac.Pic
        buf.position(entryStart);
        byte[] rawName = new byte[8];
        buf.get(rawName);
        String name = new String(rawName, StandardCharsets.ISO_8859_1).stripTrailing();
        int count = buf.getShort() & 0xFFFF;
        buf.getShort(); // 0xABCD marker

        String type = switch (count) {
            case 9 -> "BOX";
            case 3 -> "LINE";
            case 1 -> "SINGLE";
            default -> "GROUP"; // Technically possible, should not exist
        };

        List<ResourceBank.Image> images = new ArrayList<>(count);
        int pos = buf.position();
        for (int k = 0; k < count && pos < entryEnd; k++) {
            int nextPos = findNextPacPic(buf.array(), pos + 1, entryEnd);
            int imgEnd = (k + 1 < count && nextPos > 0) ? nextPos : entryEnd;
            buf.position(pos);
            images.add(readPacPic(buf, pos, imgEnd));
            pos = imgEnd;
        }
        return new ResourceBank.Element(name, type, images);
    }

    // -------------------------------------------------------------------------
    // Coordinate-based element grouper
    // -------------------------------------------------------------------------

    /**
     * Groups consecutive untyped single-image elements into BOX or LINE elements
     * based on the spatial arrangement of their source coordinates.
     *
     * <p>Named elements (those with an explicit {@code type}) pass through unchanged.
     *
     * <p>Detection order (greedy, tries BOX before LINE):
     * <ol>
     *   <li><b>BOX</b>: nine consecutive untyped single images forming a 3×3 grid
     *       with uniform tile width and height.</li>
     *   <li><b>LINE</b>: three consecutive untyped single images sharing the same tile
     *       size and arranged either horizontally (same y, x increments by width) or
     *       vertically (same x, y increments by height).</li>
     *   <li>Otherwise: left as a single-image element with no type.</li>
     * </ol>
     */
    private static List<ResourceBank.Element> groupElements(List<ResourceBank.Element> raw) {
        List<ResourceBank.Element> result = new ArrayList<>();
        int i = 0;
        while (i < raw.size()) {
            ResourceBank.Element el = raw.get(i);

            // Only merge untyped single-image entries (simple Pac.Pics)
            if (el.type() != null || el.images().size() != 1) {
                result.add(el);
                i++;
                continue;
            }

            if (i + 9 <= raw.size() && allUntypedSingle(raw, i, 9) && isBox(raw, i)) {
                result.add(new ResourceBank.Element(null, "BOX", flatImages(raw, i, 9)));
                i += 9;
            } else if (i + 3 <= raw.size() && allUntypedSingle(raw, i, 3) && isLine(raw, i)) {
                var images = flatImages(raw, i, 3);
                String type = "LINE";
                if(images.getFirst().x() < images.getLast().x() && images.getFirst().y() == images.getLast().y()) {
                    type = "HLINE";
                } else if(images.getFirst().y() < images.getLast().y() && images.getFirst().x() == images.getLast().x()) {
                    type = "VLINE";
                }
                result.add(new ResourceBank.Element(null, type, images));
                i += 3;
            } else {
                result.add(el);
                i++;
            }
        }
        return result;
    }

    private static boolean allUntypedSingle(List<ResourceBank.Element> elems, int start, int count) {
        for (int k = 0; k < count; k++) {
            ResourceBank.Element el = elems.get(start + k);
            if (el.type() != null || el.images().size() != 1) return false;
        }
        return true;
    }

    /**
     * Checks whether nine consecutive single-image elements form a 3×3 coordinate grid.
     */
    private static boolean isBox(List<ResourceBank.Element> elems, int start) {
        ResourceBank.Image ref = elems.get(start).images().get(0);
        int w = ref.width(), h = ref.height();
        int x0 = ref.x(), y0 = ref.y();
        for (int k = 0; k < 9; k++) {
            ResourceBank.Image img = elems.get(start + k).images().get(0);
            if (img.width() != w || img.height() != h) return false;
            if (img.x() != x0 + (k % 3) * w) return false;
            if (img.y() != y0 + (k / 3) * h) return false;
        }
        return true;
    }

    /**
     * Checks whether three consecutive single-image elements form a horizontal or vertical line.
     */
    private static boolean isLine(List<ResourceBank.Element> elems, int start) {
        ResourceBank.Image a = elems.get(start).images().get(0);
        ResourceBank.Image b = elems.get(start + 1).images().get(0);
        ResourceBank.Image c = elems.get(start + 2).images().get(0);
        int w = a.width(), h = a.height();
        if (b.width() != w || b.height() != h || c.width() != w || c.height() != h) return false;
        boolean hLine = b.x() == a.x() + w && c.x() == a.x() + 2 * w
                        && b.y() == a.y() && c.y() == a.y();
        boolean vLine = b.y() == a.y() + h && c.y() == a.y() + 2 * h
                        && b.x() == a.x() && c.x() == a.x();
        return hLine || vLine;
    }

    private static List<ResourceBank.Image> flatImages(List<ResourceBank.Element> elems, int start, int count) {
        List<ResourceBank.Image> imgs = new ArrayList<>(count);
        for (int k = 0; k < count; k++) {
            imgs.add(elems.get(start + k).images().get(0));
        }
        return imgs;
    }

    // -------------------------------------------------------------------------
    // Pac.Pic parsing helpers
    // -------------------------------------------------------------------------

    /**
     * Reads one Pac.Pic image starting at {@code start}.
     *
     * <p>Header layout after the 4-byte magic {@code 0x06071963}:
     * <pre>
     *   [2] Pkdx   source X in bytes  (pixel X = Pkdx × 8)
     *   [2] Pkdy   source Y in pixels
     *   [2] Pktx   width in bytes     (pixel width = Pktx × 8)
     *   [2] Pkty   height in lumps
     *   [2] Pktcar lines per lump     (total height = Pkty × Pktcar)
     *   [2] Pkplan number of bitplanes
     *   [4] next_data pointer   (Amiga address, not meaningful outside Amiga)
     *   [4] next_pointer pointer
     *   --- packed bitmap data ---
     * </pre>
     */
    private static ResourceBank.Image readPacPic(ByteBuffer buf, int start, int end) {
        buf.position(start + 4); // skip magic
        int pkdx = buf.getShort() & 0xFFFF;
        int pkdy = buf.getShort() & 0xFFFF;
        int pktx = buf.getShort() & 0xFFFF;
        int pkty = buf.getShort() & 0xFFFF;
        int pktcar = buf.getShort() & 0xFFFF;
        int pkplan = buf.getShort() & 0xFFFF;

        byte[] data = new byte[end - start];
        buf.position(start);
        buf.get(data);

        return new ResourceBank.Image(
                pkdx * 8, pkdy,
                pktx * 8, pkty * pktcar,
                pkplan, data);
    }

    /**
     * Finds the next {@code 0x06071963} magic in {@code data[from..end)}, returning its offset or -1.
     */
    private static int findNextPacPic(byte[] data, int from, int end) {
        for (int o = from; o <= end - 4; o++) {
            if (data[o] == 0x06 && data[o + 1] == 0x07 && data[o + 2] == 0x19 && data[o + 3] == 0x63)
                return o;
        }
        return -1;
    }

    // -------------------------------------------------------------------------
    // Texts section
    // -------------------------------------------------------------------------

    private static List<String> parseTexts(ByteBuffer buf) {
        List<String> texts = new ArrayList<>();
        while (buf.hasRemaining()) {
            int marker = buf.get() & 0xFF;
            if (marker != 0x00) break;
            int len = buf.get() & 0xFF;
            if (len == 0xFF) break;
            byte[] strBytes = new byte[len];
            buf.get(strBytes);
            texts.add(new String(strBytes, StandardCharsets.ISO_8859_1));
        }
        return texts;
    }

    // -------------------------------------------------------------------------
    // DBL (Interface programs) section
    // -------------------------------------------------------------------------

    private static List<String> parseDBL(ByteBuffer buf) {
        int dblStart = buf.position();
        int nPrograms = buf.getShort() & 0xFFFF;
        int[] offsets = new int[nPrograms];
        for (int i = 0; i < nPrograms; i++) {
            offsets[i] = buf.getInt();
        }

        List<String> programs = new ArrayList<>(nPrograms);
        for (int i = 0; i < nPrograms; i++) {
            buf.position(dblStart + offsets[i]);
            // Length word spans from Prog_N to Prog_N_End (includes the 2-byte length word itself)
            int progLen = (buf.getShort() & 0xFFFF) - 2;
            byte[] progBytes = new byte[progLen];
            buf.get(progBytes);
            int strLen = progLen;
            while (strLen > 0 && progBytes[strLen - 1] == 0) strLen--;
            programs.add(new String(progBytes, 0, strLen, StandardCharsets.ISO_8859_1));
        }
        return programs;
    }
}
