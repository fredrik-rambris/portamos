package dev.rambris.iff.codec;

import dev.rambris.iff.exceptions.IffParseException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed ILBM {@code BMHD} (BitMapHeader) chunk.
 *
 * <p>Binary layout (all big-endian):
 * <pre>
 *   [2]  width          — raster width in pixels
 *   [2]  height         — raster height in pixels
 *   [2]  x              — pixel x origin
 *   [2]  y              — pixel y origin
 *   [1]  planes         — number of bitplanes
 *   [1]  masking        — masking technique (0=none, 1=mask plane, 2=transparent color)
 *   [1]  compression    — 0=none, 1=ByteRun1
 *   [1]  pad1           — reserved, must be 0
 *   [2]  transparentColor
 *   [1]  xAspect        — pixel aspect ratio X
 *   [1]  yAspect        — pixel aspect ratio Y
 *   [2]  pageWidth      — source page width in pixels
 *   [2]  pageHeight     — source page height in pixels
 * </pre>
 */
public record BmhdChunk(
        int width,
        int height,
        int x,
        int y,
        int planes,
        int masking,
        int compression,
        int transparentColor,
        int xAspect,
        int yAspect,
        int pageWidth,
        int pageHeight
) {

    public static final int COMPRESSION_NONE     = 0;
    public static final int COMPRESSION_BYTERUN1 = 1;

    /** Parses a {@code BMHD} chunk payload. */
    public static BmhdChunk parse(byte[] data) {
        if (data.length < 20) {
            throw new IffParseException("BMHD chunk too small: " + data.length + " bytes");
        }
        var buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        int width            = buf.getShort() & 0xFFFF;
        int height           = buf.getShort() & 0xFFFF;
        int x                = buf.getShort();
        int y                = buf.getShort();
        int planes           = buf.get() & 0xFF;
        int masking          = buf.get() & 0xFF;
        int compression      = buf.get() & 0xFF;
        buf.get();                               // pad1
        int transparentColor = buf.getShort() & 0xFFFF;
        int xAspect          = buf.get() & 0xFF;
        int yAspect          = buf.get() & 0xFF;
        int pageWidth        = buf.getShort();
        int pageHeight       = buf.getShort();
        return new BmhdChunk(width, height, x, y, planes, masking, compression,
                transparentColor, xAspect, yAspect, pageWidth, pageHeight);
    }

    /** Encodes this record to its 20-byte binary representation. */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        buf.putShort((short) width);
        buf.putShort((short) height);
        buf.putShort((short) x);
        buf.putShort((short) y);
        buf.put((byte) planes);
        buf.put((byte) masking);
        buf.put((byte) compression);
        buf.put((byte) 0);          // pad1
        buf.putShort((short) transparentColor);
        buf.put((byte) xAspect);
        buf.put((byte) yAspect);
        buf.putShort((short) pageWidth);
        buf.putShort((short) pageHeight);
        return buf.array();
    }
}
