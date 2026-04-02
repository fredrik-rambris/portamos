package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads an AMOS Professional Work or Data bank ({@code .Abk}) file.
 *
 * <p>Binary layout (big-endian):
 * <pre>
 *   [4]  "AmBk"
 *   [2]  bank number
 *   [2]  flags: 0x0000 = chip RAM, 0x0001 = fast (non-chip) RAM
 *   [4]  length: (name size 8) + payload size
 *   [8]  bank name: "Work    " or "Data    "
 *   [n]  payload bytes
 * </pre>
 *
 * <p><b>Note on bit 31 of the length field:</b> the AMOS Pro saver sets bit 31 of the
 * length field for Data banks (discovered by inspecting {@code +Lib.s}).  However,
 * AMOS itself determines the bank type from the 8-byte name, not from that bit.
 * We therefore mask out bit 31 when reading and never set it when writing, keeping
 * the format compatible with AMOS while avoiding a spurious dependency on an
 * undocumented flag.
 */
public class RawBankReader {

    /**
     * Reads a Work or Data bank from {@code path}.
     *
     * @return a {@link RawBank} with type determined from the bank name
     * @throws IOException if the file cannot be read or is not a valid Work/Data bank
     */
    public AmosBank read(Path path) throws IOException {
        byte[] raw = Files.readAllBytes(path);
        ByteBuffer buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        // Magic
        byte[] magicBytes = new byte[4];
        buf.get(magicBytes);
        if (!"AmBk".equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBk file: " + path);
        }

        short bankNumber = buf.getShort();
        int   flags      = buf.getShort() & 0xFFFF;
        // Bit 31 of the length field is set by the AMOS saver for Data banks but is
        // not used by the AMOS loader — type is determined from the name below.
        int   nameAndPayload = buf.getInt() & 0x7FFFFFFF;

        boolean chipRam = (flags & 0x0001) == 0; // 0x0000 = chip, 0x0001 = fast

        // Bank name determines type (8 bytes, space-padded to match Type.identifier())
        byte[] nameBytes = new byte[8];
        buf.get(nameBytes);
        String name = new String(nameBytes, StandardCharsets.ISO_8859_1);

        int payloadSize = nameAndPayload - 8;
        if (payloadSize <= 0) throw new IOException("Invalid bank length in " + path);
        byte[] data = new byte[payloadSize];
        buf.get(data);

        if (name.equals(AmosBank.Type.WORK.identifier())) return new RawBank(AmosBank.Type.WORK, bankNumber, chipRam, data);
        if (name.equals(AmosBank.Type.DATA.identifier())) return new RawBank(AmosBank.Type.DATA, bankNumber, chipRam, data);
        throw new IOException("Expected \"" + AmosBank.Type.WORK.identifier().stripTrailing()
                + "\" or \"" + AmosBank.Type.DATA.identifier().stripTrailing()
                + "\" bank name, got: \"" + name.stripTrailing() + "\" in " + path);
    }
}
