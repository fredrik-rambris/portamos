package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes a {@link PacPicBank} to the AMOS {@code AmBk} + {@code "Pac.Pic."} format.
 */
public class PacPicBankWriter implements BankWriter {


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
        return AmBkCodec.build(pb.bankNumber(), pb.chipRam(), AmosBank.Type.PACPIC.identifier(), payload);
    }

    private static byte[] serializePayload(PacPicBank bank) {
        if (!bank.isSpack()) {
            return bank.picData();
        }

        var sh = bank.screenHeader();
        var buf = ByteBuffer.allocate(PacPicFormat.SPACK_HEADER_SIZE + bank.picData().length).order(ByteOrder.BIG_ENDIAN);
        buf.putInt(PacPicFormat.SPACK_MAGIC);
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

