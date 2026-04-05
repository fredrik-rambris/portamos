package dev.rambris.amigaamos.bank;

import java.io.IOException;
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
        SAMPLES("Samples "),
        SPRITES("Sprites "),
        ICONS("Icons   ");

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
     * based on the 4-byte magic and (for {@code AmBk} files) the 8-byte bank-name header field.
     *
     * <ul>
     *   <li><b>AmSp / AmIc</b> → {@link SpriteBank} via {@link SpriteBankReader}</li>
     *   <li><b>AmBk / Resource</b> → {@link ResourceBank} via {@link ResourceBankReader}</li>
     *   <li><b>AmBk / Pac.Pic.</b> → {@link PacPicBank} via {@link PacPicBankReader}</li>
     *   <li><b>AmBk / others</b> (Work, Data, Music, Samples, …) → {@link RawBank} via {@link RawBankReader}</li>
     * </ul>
     */
    static AmosBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads an AMOS bank from raw bytes, dispatching to the correct reader
     * based on the 4-byte magic and (for {@code AmBk} files) the 8-byte bank-name header field.
     */
    static AmosBank read(byte[] data) throws IOException {
        if (data.length < 4) {
            throw new IOException("Too small to be an AMOS bank (" + data.length + " bytes)");
        }

        var magic = new String(data, 0, 4, StandardCharsets.US_ASCII);

        return switch (magic) {
            case "AmSp", "AmIc" -> SpriteBankReader.read(data);
            case "AmBk" -> {
                if (data.length < MIN_HEADER_SIZE) {
                    throw new IOException("Too small to be an AmBk bank (" + data.length + " bytes)");
                }
                var nameBytes = new byte[8];
                System.arraycopy(data, 12, nameBytes, 0, 8);
                var bankName = new String(nameBytes, StandardCharsets.ISO_8859_1);
                var type = Type.fromIdentifier(bankName);
                if (type == Type.RESOURCE) yield ResourceBankReader.read(data);
                if (type == Type.PACPIC) yield PacPicBankReader.read(data);
                yield RawBankReader.read(data);
            }
            default -> throw new IOException("Not an AMOS bank file: magic=\"" + magic + "\"");
        };
    }
}
