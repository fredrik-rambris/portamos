package dev.rambris.amigaamos.bank;

import java.io.IOException;
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
        // Bit 31 is intentionally not set even for Data banks; AMOS resolves type by name.
        return AmBkCodec.build(rb.bankNumber(), rb.chipRam(), rb.type().identifier(), rb.data());
    }
}
