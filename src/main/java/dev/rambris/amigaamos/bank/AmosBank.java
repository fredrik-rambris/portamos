package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface AmosBank {

    int MIN_HEADER_SIZE = 20; // 4 magic + 2 num + 2 flags + 4 len + 8 name

    enum Type {
        MUSIC("Music   "),
        TRACKER("Tracker "),
        AMAL("Amal    "),
        MENU("Menu    "),
        DATAS("Datas   "),
        DATA("Data    "),
        WORK("Work    "),
        ASM("Asm     "),
        CODE("Code    "),
        PACPIC("Pac.Pic."),
        RESOURCE("Resource"),
        SAMPLES("Samples ");

        private final String identifier;

        Type(String identifier) {
            this.identifier = identifier;
        }

        public String identifier() {
            return identifier;
        }

        public static Type fromIdentifier(String id) {
            for (var t : values()) {
                if (t.identifier.equals(id)) return t;
            }
            throw new IllegalArgumentException("Unknown identifier \"" + id + "\"");
        }
    }

    Type type();

    short bankNumber();

    boolean chipRam();

    BankWriter writer();

    /**
     * Reads an AMOS bank from a file, dispatching to the correct reader
     * based on the 8-byte bank-name header field.
     *
     * <ul>
     *   <li><b>Resource</b> → {@link ResourceBank} via {@link ResourceBankReader}</li>
     *   <li><b>All others</b> (Work, Data, Music, Samples, …) → {@link RawBank} via {@link RawBankReader}</li>
     * </ul>
     */
    static AmosBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads an AMOS bank from raw bytes, dispatching to the correct reader
     * based on the 8-byte bank-name header field.
     */
    static AmosBank read(byte[] data) throws IOException {
        if (data.length < MIN_HEADER_SIZE) {
            throw new IOException("Too small to be an AmBk bank (" + data.length + " bytes)");
        }

        var buf = ByteBuffer.wrap(data, 0, MIN_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);

        var magicBytes = new byte[4];
        buf.get(magicBytes);
        var magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!"AmBk".equals(magic)) {
            throw new IOException("Not an AmBk file: magic=\"" + magic + "\"");
        }

        // Skip bank number (2), flags (2), length (4) → offset 12
        buf.position(12);
        var nameBytes = new byte[8];
        buf.get(nameBytes);
        var bankName = new String(nameBytes, StandardCharsets.ISO_8859_1);

        var type = Type.fromIdentifier(bankName);

        if (type == Type.RESOURCE) {
            return ResourceBankReader.read(data);
        }
        return RawBankReader.read(data);
    }
}
