package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads an AMOS {@code AmBk} bank whose type is {@code "Pac.Pic."}.
 */
class PacPicBankReader {


    static PacPicBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    static PacPicBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        if (hdr.type() != AmosBank.Type.PACPIC) {
            throw new IOException("Expected \"" + AmosBank.Type.PACPIC.identifier()
                    + "\" bank, got: \"" + hdr.typeName() + "\"");
        }

        return parsePayload(hdr.bankNumber(), hdr.chipRam(), hdr.payload());
    }

    private static PacPicBank parsePayload(short bankNumber, boolean chipRam, byte[] payload)
            throws IOException {
        if (payload.length < 4) throw new IOException("Pac.Pic payload is too short");

        var first = beInt(payload, 0);

        if (first == PacPicFormat.SPACK_MAGIC) {
            if (payload.length < PacPicFormat.SPACK_HEADER_SIZE + 4) {
                throw new IOException("SPACK payload is too short");
            }
            var picMagic = beInt(payload, PacPicFormat.SPACK_HEADER_SIZE);
            if (picMagic != PacPicFormat.PK_MAGIC) {
                throw new IOException("SPACK payload missing Pac.Pic image after screen header");
            }
            var sh = parseScreenHeader(payload);
            var picData = new byte[payload.length - PacPicFormat.SPACK_HEADER_SIZE];
            System.arraycopy(payload, PacPicFormat.SPACK_HEADER_SIZE, picData, 0, picData.length);
            return new PacPicBank(bankNumber, chipRam, sh, picData);
        }

        if (first == PacPicFormat.PK_MAGIC) {
            return new PacPicBank(bankNumber, chipRam, null, payload);
        }

        throw new IOException("Pac.Pic payload does not start with SPACK or PACK magic");
    }

    private static PacPicBank.ScreenHeader parseScreenHeader(byte[] payload) {
        var b = ByteBuffer.wrap(payload, 0, PacPicFormat.SPACK_HEADER_SIZE).order(ByteOrder.BIG_ENDIAN);
        b.getInt(); // SPACK_MAGIC

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

