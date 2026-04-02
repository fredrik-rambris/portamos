package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes a {@link RawBank} to the AMOS Professional
 * Work/Data bank binary format ({@code .Abk}).
 *
 * <p>This is the inverse of {@link RawBankReader}; see that class for the format
 * description.
 */
public class RawBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (bank instanceof RawBank rb) {
            return serialize(rb);
        }
        throw new IllegalArgumentException("Not a Work or Data bank: " + bank.type().identifier().stripTrailing());
    }

    private static byte[] serialize(RawBank rb) {
        byte[] nameBytes = rb.type().identifier().getBytes(StandardCharsets.ISO_8859_1);

        // Length = name (8) + payload.  Bit 31 is NOT set even for Data banks:
        // AMOS determines the type from the name, not from that bit, so we keep
        // the field clean.  (The original AMOS saver does set bit 31 for Data banks,
        // but this is undocumented and the loader ignores it.)
        int nameAndPayload = nameBytes.length + rb.data().length;
        // flags: 0x0000 = chip RAM, 0x0001 = fast RAM
        int flags = rb.chipRam() ? 0x0000 : 0x0001;

        ByteBuffer buf = ByteBuffer.allocate(4 + 2 + 2 + 4 + nameBytes.length + rb.data().length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put("AmBk".getBytes(StandardCharsets.US_ASCII));
        buf.putShort(rb.bankNumber());
        buf.putShort((short) flags);
        buf.putInt(nameAndPayload);
        buf.put(nameBytes);
        buf.put(rb.data());
        return buf.array();
    }
}
