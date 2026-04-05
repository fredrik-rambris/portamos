package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads an AMOS {@code AmBk} bank whose type is {@code "Pac.Pic."}.
 */
public class PacPicBankReader {

    private static final int PS_MAGIC = 0x12031990;
    private static final int PK_MAGIC = 0x06071963;
    private static final int SPACK_HEADER_SIZE = 90;

    public static PacPicBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static PacPicBank read(byte[] raw) throws IOException {
        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        var magicBytes = new byte[4];
        buf.get(magicBytes);
        if (!"AmBk".equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBk file");
        }

        var bankNumber = buf.getShort();
        var flags = buf.getShort() & 0xFFFF;
        var nameAndPayload = buf.getInt() & 0x7FFFFFFF;
        var chipRam = (flags & 0x0001) == 0;

        var nameBytes = new byte[8];
        buf.get(nameBytes);
        var name = new String(nameBytes, StandardCharsets.ISO_8859_1);
        if (!AmosBank.Type.PACPIC.identifier().equals(name)) {
            throw new IOException("Expected \"" + AmosBank.Type.PACPIC.identifier()
                    + "\" bank, got: \"" + name + "\"");
        }

        var payloadSize = nameAndPayload - 8;
        if (payloadSize <= 0) throw new IOException("Invalid bank length");
        var payload = new byte[payloadSize];
        buf.get(payload);

        return parsePayload(bankNumber, chipRam, payload);
    }

    private static PacPicBank parsePayload(short bankNumber, boolean chipRam, byte[] payload)
            throws IOException {
        if (payload.length < 4) throw new IOException("Pac.Pic payload is too short");

        var first = beInt(payload, 0);

        if (first == PS_MAGIC) {
            if (payload.length < SPACK_HEADER_SIZE + 4) {
                throw new IOException("SPACK payload is too short");
            }
            var picMagic = beInt(payload, SPACK_HEADER_SIZE);
            if (picMagic != PK_MAGIC) {
                throw new IOException("SPACK payload missing Pac.Pic image after screen header");
            }
            var sh = parseScreenHeader(payload);
            var picData = new byte[payload.length - SPACK_HEADER_SIZE];
            System.arraycopy(payload, SPACK_HEADER_SIZE, picData, 0, picData.length);
            return new PacPicBank(bankNumber, chipRam, sh, picData);
        }

        if (first == PK_MAGIC) {
            return new PacPicBank(bankNumber, chipRam, null, payload);
        }

        throw new IOException("Pac.Pic payload does not start with SPACK or PACK magic");
    }

    private static PacPicBank.ScreenHeader parseScreenHeader(byte[] payload) {
        var b = ByteBuffer.wrap(payload, 0, SPACK_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.getInt(); // PS_MAGIC

        var width = b.getShort() & 0xFFFF;
        var height = b.getShort() & 0xFFFF;
        var hardX = b.getShort() & 0xFFFF;
        var hardY = b.getShort() & 0xFFFF;
        var displayWidth = b.getShort() & 0xFFFF;
        var displayHeight = b.getShort() & 0xFFFF;
        var offsetX = b.getShort() & 0xFFFF;
        var offsetY = b.getShort() & 0xFFFF;
        var bplCon0 = b.getShort() & 0xFFFF;
        var numColors = b.getShort() & 0xFFFF;
        var numPlanes = b.getShort() & 0xFFFF;

        var palette = new int[32];
        for (int i = 0; i < 32; i++) {
            palette[i] = b.getShort() & 0xFFFF;
        }

        return new PacPicBank.ScreenHeader(
                width, height,
                hardX, hardY,
                displayWidth, displayHeight,
                offsetX, offsetY,
                bplCon0, numColors, numPlanes,
                palette
        );
    }

    private static int beInt(byte[] data, int off) {
        return ((data[off] & 0xFF) << 24)
                | ((data[off + 1] & 0xFF) << 16)
                | ((data[off + 2] & 0xFF) << 8)
                | (data[off + 3] & 0xFF);
    }
}

