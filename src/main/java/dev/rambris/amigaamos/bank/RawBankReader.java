package dev.rambris.amigaamos.bank;

import java.io.IOException;
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
        var hdr = AmBkCodec.parse(raw);
        return new RawBank(hdr.type(), hdr.bankNumber(), hdr.chipRam(), hdr.payload());
    }
}
