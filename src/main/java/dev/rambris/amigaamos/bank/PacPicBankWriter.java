package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes a {@link PacPicBank} to the AMOS {@code AmBk} + {@code "Pac.Pic."} format.
 */
public class PacPicBankWriter implements BankWriter {

    private static final int PS_MAGIC = 0x12031990;

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (!(bank instanceof PacPicBank pb)) {
            throw new IllegalArgumentException("Not a PacPicBank: " + bank.type());
        }

        var payload = serializePayload(pb);
        var nameBytes = AmosBank.Type.PACPIC.identifier().getBytes(StandardCharsets.ISO_8859_1);
        var nameAndPayload = nameBytes.length + payload.length;
        var flags = pb.chipRam() ? 0x0000 : 0x0001;

        var buf = ByteBuffer.allocate(4 + 2 + 2 + 4 + nameBytes.length + payload.length)
                .order(ByteOrder.BIG_ENDIAN);
        buf.put("AmBk".getBytes(StandardCharsets.US_ASCII));
        buf.putShort(pb.bankNumber());
        buf.putShort((short) flags);
        buf.putInt(nameAndPayload);
        buf.put(nameBytes);
        buf.put(payload);
        return buf.array();
    }

    private static byte[] serializePayload(PacPicBank bank) {
        if (!bank.isSpack()) {
            return bank.picData();
        }

        var sh = bank.screenHeader();
        var buf = ByteBuffer.allocate(90 + bank.picData().length).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(PS_MAGIC);
        buf.putShort((short) sh.width());
        buf.putShort((short) sh.height());
        buf.putShort((short) sh.hardX());
        buf.putShort((short) sh.hardY());
        buf.putShort((short) sh.displayWidth());
        buf.putShort((short) sh.displayHeight());
        buf.putShort((short) sh.offsetX());
        buf.putShort((short) sh.offsetY());
        buf.putShort((short) sh.bplCon0());
        buf.putShort((short) sh.numColors());
        buf.putShort((short) sh.numPlanes());
        for (int i = 0; i < 32; i++) {
            buf.putShort((short) (i < sh.palette().length ? sh.palette()[i] : 0));
        }
        buf.put(bank.picData());
        return buf.array();
    }
}

