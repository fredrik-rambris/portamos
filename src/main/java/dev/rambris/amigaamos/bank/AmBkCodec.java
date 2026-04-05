package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;

/**
 * Shared codec for the common AMOS {@code AmBk} envelope.
 *
 * <p>Payload semantics differ by bank type and are handled by type-specific readers/writers.
 */
public final class AmBkCodec {

    private static final int MIN_AMBK_SIZE = 20;

    private AmBkCodec() {
    }

    /** Parsed AmBk header + payload bytes. */
    public record AmBkHeader(
            short bankNumber,
            boolean chipRam,
            String typeName,
            byte[] payload
    ) {
        public AmosBank.Type type() {
            return AmosBank.Type.fromIdentifier(typeName);
        }
    }

    /**
     * Parses an {@code AmBk} file into header fields and payload bytes.
     */
    public static AmBkHeader parse(byte[] raw) throws IOException {
        if (raw.length < MIN_AMBK_SIZE) {
            throw new IOException("Too small to be an AmBk file (" + raw.length + " bytes)");
        }

        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        var magicBytes = new byte[4];
        buf.get(magicBytes);
        if (!"AmBk".equals(new String(magicBytes, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBk file");
        }

        var bankNumber = buf.getShort();
        var flags = buf.getShort() & 0xFFFF;
        // Bit 31 can be set by AMOS for some bank kinds; loader uses name for type.
        var nameAndPayload = buf.getInt() & 0x7FFFFFFF;
        var chipRam = (flags & 0x0001) == 0; // 0x0000 = chip, 0x0001 = fast

        var nameBytes = new byte[8];
        buf.get(nameBytes);
        var typeName = new String(nameBytes, StandardCharsets.ISO_8859_1);

        var payloadSize = nameAndPayload - 8;
        if (payloadSize <= 0) throw new IOException("Invalid bank length");
        if (buf.remaining() < payloadSize) {
            throw new IOException("Truncated AmBk payload");
        }

        var payload = new byte[payloadSize];
        buf.get(payload);

        return new AmBkHeader(bankNumber, chipRam, typeName, payload);
    }

    /**
     * Reads only the {@code AmBk} bank type from the 8-byte type-name field.
     */
    public static AmosBank.Type typeOf(byte[] raw) throws IOException {
        if (raw.length < MIN_AMBK_SIZE) {
            throw new IOException("Too small to be an AmBk file (" + raw.length + " bytes)");
        }

        if (!"AmBk".equals(new String(raw, 0, 4, StandardCharsets.US_ASCII))) {
            throw new IOException("Not an AmBk file");
        }

        var typeName = new String(raw, 12, 8, StandardCharsets.ISO_8859_1);
        return AmosBank.Type.fromIdentifier(typeName);
    }

    /**
     * Builds an {@code AmBk} file from header fields and payload bytes.
     */
    public static byte[] build(short bankNumber, boolean chipRam, String typeName, byte[] payload) {
        var nameBytes = typeName.getBytes(StandardCharsets.ISO_8859_1);
        var nameAndPayload = nameBytes.length + payload.length;
        var flags = chipRam ? 0x0000 : 0x0001;

        var buf = ByteBuffer.allocate(4 + 2 + 2 + 4 + nameBytes.length + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put("AmBk".getBytes(StandardCharsets.US_ASCII));
        buf.putShort(bankNumber);
        buf.putShort((short) flags);
        buf.putInt(nameAndPayload);
        buf.put(nameBytes);
        buf.put(payload);
        return buf.array();
    }
}

