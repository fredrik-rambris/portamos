package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public interface AmosBank {

    int MIN_HEADER_SIZE = 20; // 4 magic + 2 num + 2 flags + 4 len + 8 name

    @FunctionalInterface
    interface AmBkReader {
        AmosBank read(byte[] data) throws IOException;
    }

    enum Type {
        MUSIC("Music   ", RawBankReader::read),
        TRACKER("Tracker ", RawBankReader::read),
        AMAL("Amal    ", RawBankReader::read),
        MENU("Menu    ", RawBankReader::read),
        DATAS("Datas   ", RawBankReader::read),
        DATA("Data    ", RawBankReader::read),
        WORK("Work    ", RawBankReader::read),
        ASM("Asm     ", RawBankReader::read),
        CODE("Code    ", RawBankReader::read),
        PACPIC("Pac.Pic.", PacPicBankReader::read),
        RESOURCE("Resource", ResourceBankReader::read),
        SAMPLES("Samples ", RawBankReader::read),
        SPRITES("Sprites ", RawBankReader::read),
        ICONS("Icons   ", RawBankReader::read);

        private final String identifier;
        private final AmBkReader amBkReader;

        Type(String identifier, AmBkReader amBkReader) {
            this.identifier = identifier;
            this.amBkReader = amBkReader;
        }

        public String identifier() {
            return identifier;
        }

        public AmosBank readAmBk(byte[] data) throws IOException {
            return amBkReader.read(data);
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
    public static AmosBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads an AMOS bank from raw bytes, dispatching to the correct reader
     * based on the 4-byte magic and (for {@code AmBk} files) the 8-byte bank-name header field.
     */
    public static AmosBank read(byte[] data) throws IOException {
        if (data.length < 4) {
            throw new IOException("Too small to be an AMOS bank (" + data.length + " bytes)");
        }

        var magic = new String(data, 0, 4, StandardCharsets.US_ASCII);

        return switch (magic) {
            case "AmSp", "AmIc" -> SpriteBankReader.read(data);
            case "AmBk" -> {
                var type = AmBkCodec.typeOf(data);
                yield type.readAmBk(data);
            }
            default -> throw new IOException("Not an AMOS bank file: magic=\"" + magic + "\"");
        };
    }
}
