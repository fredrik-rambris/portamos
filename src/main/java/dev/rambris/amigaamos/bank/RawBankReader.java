package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads an AMOS Professional bank ({@code .Abk}) file as a {@link RawBank}.
 *
 * <p>Handles any bank type whose payload is opaque binary data (Work, Data,
 * Music, Samples, AMAL, etc.).  The bank type is determined from the 8-byte
 * name field in the header.
 *
 * <p>Binary layout (big-endian):
 * <pre>
 *   [4]  "AmBk"
 *   [2]  bank number
 *   [2]  flags: 0x0000 = chip RAM, 0x0001 = fast (non-chip) RAM
 *   [4]  length: (name size 8) + payload size
 *   [8]  bank name: "Work    ", "Data    ", "Music   ", …
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
     * Reads a bank from {@code path}.
     *
     * @return a {@link RawBank} with type determined from the bank name
     * @throws IOException if the file cannot be read or is not a valid bank
     */
    public static RawBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads a bank from raw bytes.
     *
     * @return a {@link RawBank} with type determined from the bank name
     * @throws IOException if the data is not a valid bank
     */
    public static RawBank read(byte[] raw) throws IOException {
        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        // Magic
        var magicBytes = new byte[4];
        buf.get(magicBytes);
        if (!"AmBk".equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBk file");
        }

        var bankNumber = buf.getShort();
        var flags = buf.getShort() & 0xFFFF;
        // Bit 31 of the length field is set by the AMOS saver for Data banks but is
        // not used by the AMOS loader — type is determined from the name below.
        var nameAndPayload = buf.getInt() & 0x7FFFFFFF;

        var chipRam = (flags & 0x0001) == 0; // 0x0000 = chip, 0x0001 = fast

        // Bank name determines type (8 bytes, space-padded to match Type.identifier())
        var nameBytes = new byte[8];
        buf.get(nameBytes);
        var name = new String(nameBytes, StandardCharsets.ISO_8859_1);

        var payloadSize = nameAndPayload - 8;
        if (payloadSize <= 0) throw new IOException("Invalid bank length");
        var data = new byte[payloadSize];
        buf.get(data);

        var type = AmosBank.Type.fromIdentifier(name);
        if (type == null) {
            throw new IOException("Unknown bank type: \"" + name.stripTrailing() + "\"");
        }
        return new RawBank(type, bankNumber, chipRam, data);
    }
}
