/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

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
        MUSIC("Music   ", MusicBankReader::read, MusicBank.class),
        TRACKER("Tracker ", TrackerBankReader::read, TrackerBank.class),
        AMAL("Amal    ", AmalBankReader::read, AmalBank.class),
        MENU("Menu    ", MenuBankReader::read, MenuBank.class),
        DATAS("Datas   ", RawBankReader::read, RawBank.class),
        DATA("Data    ", RawBankReader::read, RawBank.class),
        WORK("Work    ", RawBankReader::read, RawBank.class),
        ASM("Asm     ", RawBankReader::read, RawBank.class),
        CODE("Code    ", RawBankReader::read, RawBank.class),
        PACPIC("Pac.Pic.", PacPicBankReader::read, PacPicBank.class),
        RESOURCE("Resource", ResourceBankReader::read, ResourceBank.class),
        SAMPLES("Samples ", SampleBankReader::read, SampleBank.class),
        SPRITES("Sprites ", SpriteBankReader::read, SpriteBank.class),
        ICONS("Icons   ", SpriteBankReader::read, SpriteBank.class);

        private final String identifier;
        private final AmBkReader amBkReader;
        private final Class<? extends AmosBank> clazz;

        Type(String identifier, AmBkReader amBkReader, Class<? extends AmosBank> clazz) {
            this.identifier = identifier;
            this.amBkReader = amBkReader;
            this.clazz = clazz;
        }

        public String identifier() {
            return identifier;
        }

        public String toString() {
            return identifier().strip();
        }

        public static Type fromIdentifier(String id) {
            for (var t : values()) {
                if (t.identifier.equals(id)) return t;
            }
            throw new IllegalArgumentException("Unknown identifier \"" + id + "\"");
        }

        public Class<? extends AmosBank> bankClass() {
            return clazz;
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

        var type = identify(data);
        return type.amBkReader.read(data);
    }

    static AmosBank.Type identify(byte[] data) throws IOException {
        if (data.length < 4) {
            throw new IOException("Too small to be an AMOS bank (" + data.length + " bytes)");
        }

        var magic = new String(data, 0, 4, StandardCharsets.US_ASCII);

        return switch (magic) {
            case "AmSp" -> Type.SPRITES;
            case "AmIc" -> Type.ICONS;
            case "AmBk" -> AmBkCodec.typeOf(data);
            default -> throw new IOException("Not an AMOS bank file: magic=\"" + magic + "\"");
        };

    }
}
