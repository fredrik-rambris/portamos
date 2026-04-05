package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads an AMOS Professional Sprite or Icon bank ({@code AmSp} / {@code AmIc}) file.
 *
 * <p>Sprite banks do not follow the regular {@code AmBk} convention; they use their own magic
 * and layout.  See {@link SpriteBank} for the format description.
 */
class SpriteBankReader {

    private static final String MAGIC_SP = "AmSp";
    private static final String MAGIC_IC = "AmIc";

    /**
     * Reads a sprite/icon bank from {@code path}.
     */
    static SpriteBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    /**
     * Reads a sprite/icon bank from raw bytes.
     *
     * @throws IOException if the data does not start with {@code AmSp} or {@code AmIc}
     */
    static SpriteBank read(byte[] raw) throws IOException {
        var buf = ByteBuffer.wrap(raw).order(ByteOrder.BIG_ENDIAN);

        var magicBytes = new byte[4];
        buf.get(magicBytes);
        var magic = new String(magicBytes, StandardCharsets.US_ASCII);
        if (!MAGIC_SP.equals(magic) && !MAGIC_IC.equals(magic)) {
            throw new IOException("Not an AmSp/AmIc file: magic=\"" + magic + "\"");
        }
        var bankType = MAGIC_IC.equals(magic) ? AmosBank.Type.ICONS : AmosBank.Type.SPRITES;

        var count = buf.getShort() & 0xFFFF;
        var sprites = new ArrayList<SpriteBank.Sprite>(count);

        for (int i = 0; i < count; i++) {
            var widthWords = buf.getShort() & 0xFFFF;
            var height = buf.getShort() & 0xFFFF;
            var planes = buf.getShort() & 0xFFFF;
            var hotspotX = buf.getShort() & 0xFFFF;
            var hotspotY = buf.getShort() & 0xFFFF;

            byte[] data;
            if (widthWords == 0 && height == 0 && planes == 0) {
                // empty sprite — no pixel data follows
                data = new byte[0];
            } else {
                var size = widthWords * 2 * height * planes;
                data = new byte[size];
                buf.get(data);
            }
            sprites.add(new SpriteBank.Sprite(widthWords, height, planes, hotspotX, hotspotY, data));
        }

        // 32-entry colour palette
        var palette = new int[32];
        for (int i = 0; i < 32; i++) {
            palette[i] = buf.getShort() & 0xFFFF;
        }

        return new SpriteBank(bankType, List.copyOf(sprites), palette);
    }
}



