package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.file.Path;

public interface BankWriter {
    void write(AmosBank bank, Path dest) throws IOException;

    byte[] toBytes(AmosBank bank) throws IOException;
}
