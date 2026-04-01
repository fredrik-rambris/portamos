package dev.rambris.amos.bank;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Serializes a {@link ResourceBank} to an AMOS Professional Resource Bank binary ({@code .Abk}).
 *
 * <p>This is the inverse of {@link ResourceBankReader}.  The generated binary is structurally
 * equivalent to files produced by AMOS Pro; however, because the Pac.Pic encoder does not
 * apply compression, image data will be larger than in original AMOS-produced files.
 *
 * <p>File layout produced (big-endian):
 * <pre>
 *   [4]   "AmBk"
 *   [2]   bankNumber
 *   [2]   flags (bit 1 = chipRam)
 *   [4]   data length (bytes from DATA_START to end of file)
 *   [8]   "Resource"
 *   ---   DATA_START (offset 20) ---
 *   [2]   BKCHUNKS = 3
 *   [4]   images offset (relative to DATA_START; 0 if absent)
 *   [4]   texts  offset (relative to DATA_START; 0 if absent)
 *   [4]   DBL    offset (relative to DATA_START; 0 if absent)
 *   [4]   reserved 1
 *   [4]   reserved 2
 *   [4]   reserved 3
 *   ---   images section ---
 *   [2]   n_entries
 *   [4×n] entry offsets (relative to images-section start)
 *   [2]   n_colors
 *   [2]   screen_mode
 *   [64]  palette (32 × word)
 *   [2]   name_len
 *   [?]   name bytes (padded to even)
 *   ---   entry data ---
 *   ---   texts section ---
 *   repeating: 0x00, length_byte, text_bytes
 *   [2]   0x00 0xFF terminator
 *   ---   DBL section ---
 *   [2]   n_programs
 *   [4×n] program offsets (relative to DBL-section start)
 *   each: [2] length_including_itself + program_bytes + NUL (padded to even)
 * </pre>
 */
public class ResourceBankWriter implements BankWriter {

    private static final int SUB_HEADER_SIZE = 26; // 2 + 3×4 + 3×4

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (bank instanceof ResourceBank rb) {
            return serialize(rb);
        } else throw new IllegalArgumentException("Not a ResourceBank");
    }

    // -------------------------------------------------------------------------
    // Top-level serialization
    // -------------------------------------------------------------------------

    private byte[] serialize(ResourceBank bank) throws IOException {
        byte[] imgSection = buildImageSection(bank);
        byte[] txtSection = buildTextSection(bank);
        byte[] dblSection = buildDblSection(bank);

        // Sub-header offsets are relative to DATA_START
        int imgOff = imgSection.length > 0 ? SUB_HEADER_SIZE : 0;
        int txtOff = txtSection.length > 0 ? SUB_HEADER_SIZE + imgSection.length : 0;
        int dblOff = dblSection.length > 0 ? SUB_HEADER_SIZE + imgSection.length + txtSection.length : 0;

        int dataSize = SUB_HEADER_SIZE + imgSection.length + txtSection.length + dblSection.length;

        ByteBuffer buf = ByteBuffer.allocate(20 + dataSize).order(ByteOrder.BIG_ENDIAN);

        // ---- File header (20 bytes) ----
        buf.put("AmBk".getBytes(StandardCharsets.US_ASCII));
        buf.putShort(bank.bankNumber());
        buf.putShort((short) (bank.chipRam() ? 0x0002 : 0x0000));
        buf.putInt(dataSize);
        buf.put(Arrays.copyOf("Resource".getBytes(StandardCharsets.ISO_8859_1), 8));

        // ---- Sub-header at DATA_START ----
        buf.putShort((short) 3); // BKCHUNKS
        buf.putInt(imgOff);
        buf.putInt(txtOff);
        buf.putInt(dblOff);
        buf.putInt(0); // reserved 1
        buf.putInt(0); // reserved 2
        buf.putInt(0); // reserved 3

        buf.put(imgSection);
        buf.put(txtSection);
        buf.put(dblSection);

        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Images section
    // -------------------------------------------------------------------------

    private byte[] buildImageSection(ResourceBank bank) {
        List<ResourceBank.Element> elements = bank.elements();
        if (elements.isEmpty()) return new byte[0];

        // Flatten elements into individual offset-table entries:
        //   - Named element (name != null)  → one Res_NN entry
        //   - Unnamed element (name == null) → one Image_NN entry per image
        // (Coordinate-grouped BOX/HLINE/VLINE elements with null name were originally
        //  stored as separate Pac.Pic entries and must be written back the same way.)
        List<byte[]> entryPayloads = new ArrayList<>();
        for (ResourceBank.Element el : elements) {
            if (el.name() != null) {
                entryPayloads.add(serializeNamedEntry(el));
            } else {
                for (ResourceBank.Image img : el.images()) {
                    entryPayloads.add(img.data());
                }
            }
        }
        int n = entryPayloads.size();

        // Header: 2(n) + 4×n(offsets) + 2(nColors) + 2(screenMode) + 64(palette)
        //       + 2(nameLen) + nameBytes + padding
        byte[] nameBytes = bank.imagePath().getBytes(StandardCharsets.ISO_8859_1);
        int nameLen = nameBytes.length;
        int namePad = nameLen % 2 != 0 ? 1 : 0;
        int headerSz = 2 + 4 * n + 2 + 2 + 64 + 2 + nameLen + namePad;

        // Entry offsets are relative to the start of the images section
        int[] offsets = new int[n];
        int pos = headerSz;
        for (int i = 0; i < n; i++) {
            offsets[i] = pos;
            pos += entryPayloads.get(i).length;
        }

        ByteBuffer buf = ByteBuffer.allocate(pos).order(ByteOrder.BIG_ENDIAN);

        buf.putShort((short) n);
        for (int o : offsets) buf.putInt(o);

        buf.putShort((short) bank.getNumCols());
        buf.putShort((short) bank.screenMode());

        int[] palette = bank.palette();
        for (int i = 0; i < 32; i++) {
            buf.putShort((short) (i < palette.length ? palette[i] : 0));
        }

        buf.putShort((short) nameLen);
        buf.put(nameBytes);
        if (namePad > 0) buf.put((byte) 0);

        for (byte[] payload : entryPayloads) {
            buf.put(payload);
        }

        return buf.array();
    }

    /**
     * Serializes a named element (Res_NN) to its on-disk form:
     * 8-byte name + 2-byte image count + 2-byte 0xABCD + concatenated Pac.Pic data.
     */
    private byte[] serializeNamedEntry(ResourceBank.Element el) {
        int imgDataSize = el.images().stream().mapToInt(img -> img.data().length).sum();
        ByteBuffer buf = ByteBuffer.allocate(8 + 2 + 2 + imgDataSize).order(ByteOrder.BIG_ENDIAN);

        byte[] nameBytes = Arrays.copyOf(el.name().getBytes(StandardCharsets.ISO_8859_1), 8);
        buf.put(nameBytes);
        buf.putShort((short) el.images().size());
        buf.putShort((short) 0xABCD);
        for (ResourceBank.Image img : el.images()) {
            buf.put(img.data());
        }

        return buf.array();
    }

    // -------------------------------------------------------------------------
    // Texts section
    // -------------------------------------------------------------------------

    private byte[] buildTextSection(ResourceBank bank) {
        List<String> texts = bank.texts();
        if (texts.isEmpty()) return new byte[0];

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        for (String text : texts) {
            byte[] tb = text.getBytes(StandardCharsets.ISO_8859_1);
            baos.write(0x00);
            baos.write(tb.length);
            baos.writeBytes(tb);
        }
        baos.write(0x00);
        baos.write(0xFF);
        return baos.toByteArray();
    }

    // -------------------------------------------------------------------------
    // DBL (Interface programs) section
    // -------------------------------------------------------------------------

    private byte[] buildDblSection(ResourceBank bank) {
        List<String> programs = bank.programs();
        if (programs.isEmpty()) return new byte[0];

        int n = programs.size();

        // Serialize program payloads (length word + content + NUL, padded to even)
        List<byte[]> payloads = new ArrayList<>(n);
        for (String prog : programs) {
            byte[] strBytes = prog.getBytes(StandardCharsets.ISO_8859_1);
            // Content stored: strBytes + NUL, padded to even total
            int contentLen = strBytes.length + 1; // +1 for NUL
            if (contentLen % 2 != 0) contentLen++;
            // length word = contentLen + 2 (includes the 2-byte length word itself)
            byte[] entry = new byte[2 + contentLen];
            entry[0] = (byte) ((contentLen + 2) >> 8);
            entry[1] = (byte) (contentLen + 2);
            System.arraycopy(strBytes, 0, entry, 2, strBytes.length);
            // remaining bytes (NUL + padding) are already zero
            payloads.add(entry);
        }

        // Header: 2(n) + 4×n(offsets)
        int headerSz = 2 + 4 * n;
        int[] offsets = new int[n];
        int pos = headerSz;
        for (int i = 0; i < n; i++) {
            offsets[i] = pos;
            pos += payloads.get(i).length;
        }

        ByteBuffer buf = ByteBuffer.allocate(pos).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) n);
        for (int o : offsets) buf.putInt(o);
        for (byte[] payload : payloads) buf.put(payload);

        return buf.array();
    }
}
