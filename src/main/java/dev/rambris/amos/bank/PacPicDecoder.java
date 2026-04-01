package dev.rambris.amos.bank;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Decompresses an AMOS Professional Pac.Pic. compressed image.
 *
 * <p>The format uses three streams, all starting at offsets relative to the picture header:
 * <ul>
 *   <li><b>PICDATA</b> — actual (RLE-compressed) picture bytes, at header+24</li>
 *   <li><b>RLEDATA</b> — bitstream controlling PICDATA RLE, at header+rleOff</li>
 *   <li><b>POINTS</b>  — bitstream controlling RLEDATA RLE, at header+ptsOff; not itself compressed</li>
 * </ul>
 *
 * <p>Data ordering within a bitplane: the picture is divided vertically into equal-height
 * "lumps".  Within each lump, bytes are written column-major: all rows of column 0 first
 * (top to bottom), then column 1, etc.  After decompression the planar data is row-major
 * in the output buffer, matching the natural raster layout.
 *
 * <p>Reference C decompressor from the Exotica wiki:
 * <pre>
 *   int rbit=7, rrbit=6;
 *   picbyte = *picdata++;  rlebyte = *rledata++;
 *   if (*points & 0x80) rlebyte = *rledata++;
 *   for each bitplane, lump, column, row:
 *     if (rlebyte & (1 << rbit--)) picbyte = *picdata++;
 *     *d = picbyte;  d += bytes_per_line;
 *     if (rbit < 0) { rbit=7; if (*points & (1<<rrbit--)) rlebyte=*rledata++;
 *                     if (rrbit<0) { rrbit=7; points++; } }
 * </pre>
 */
public class PacPicDecoder {

    /**
     * Decompresses one Pac.Pic. image.
     *
     * @param data raw bytes starting at the {@code 0x06071963} magic
     * @return palette-index array {@code [y][x]}, dimensions {@code [height][widthPixels]}
     * @throws ArrayIndexOutOfBoundsException if the data streams are truncated
     */
    public static int[][] decompress(byte[] data) {
        ByteBuffer hdr = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        hdr.getInt();                          // 0x06071963 magic
        hdr.getShort();                        // Pkdx — source X in bytes (not used here)
        hdr.getShort();                        // Pkdy — source Y in pixels (not used here)
        int wBytes = hdr.getShort() & 0xFFFF; // Pktx — width in bytes
        int lumps  = hdr.getShort() & 0xFFFF; // Pkty — number of lumps
        int lumpH  = hdr.getShort() & 0xFFFF; // Pktcar — lines per lump
        int planes = hdr.getShort() & 0xFFFF; // Pkplan — bitplanes
        int rleOff = hdr.getInt();            // offset to RLEDATA from header start
        int ptsOff = hdr.getInt();            // offset to POINTS from header start

        int height = lumps * lumpH;
        int wPx    = wBytes * 8;

        // Planar buffers, one per bitplane, row-major: [row * wBytes + col]
        byte[][] planes_buf = new byte[planes][height * wBytes];

        // Stream positions within data[]
        int picPos = 24;      // PICDATA begins immediately after the 24-byte header
        int rlePos = rleOff;
        int ptsPos = ptsOff;

        // Initialise as per the reference decompressor
        int rbit    = 7;
        int rrbit   = 6;     // bit 7 of POINTS[0] is consumed during init
        int picbyte = data[picPos++] & 0xFF;
        int rlebyte = data[rlePos++] & 0xFF;
        if ((data[ptsPos] & 0x80) != 0) {
            rlebyte = data[rlePos++] & 0xFF;
        }

        for (int plane = 0; plane < planes; plane++) {
            byte[] buf = planes_buf[plane];
            int lumpRow = 0; // first row of current lump

            for (int j = 0; j < lumps; j++) {
                for (int col = 0; col < wBytes; col++) {
                    for (int row = 0; row < lumpH; row++) {
                        // RLE: bit 1 = new picbyte, bit 0 = repeat previous
                        if ((rlebyte & (1 << rbit)) != 0) {
                            picbyte = data[picPos++] & 0xFF;
                        }
                        rbit--;

                        // Write to row-major plane buffer
                        buf[(lumpRow + row) * wBytes + col] = (byte) picbyte;

                        // Replenish RLEDATA byte when current one is exhausted
                        if (rbit < 0) {
                            rbit = 7;
                            if ((data[ptsPos] & (1 << rrbit)) != 0) {
                                rlebyte = data[rlePos++] & 0xFF;
                            }
                            rrbit--;
                            if (rrbit < 0) {
                                rrbit = 7;
                                ptsPos++;
                            }
                        }
                    }
                }
                lumpRow += lumpH;
            }
        }

        // Interleave bitplanes into chunky palette indices
        // Amiga convention: MSB of each byte is the leftmost pixel
        int[][] pixels = new int[height][wPx];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < wPx; x++) {
                int byteIdx = y * wBytes + (x >> 3);
                int bitMask = 0x80 >> (x & 7);
                int idx = 0;
                for (int p = 0; p < planes; p++) {
                    if ((planes_buf[p][byteIdx] & bitMask) != 0) {
                        idx |= (1 << p);
                    }
                }
                pixels[y][x] = idx;
            }
        }
        return pixels;
    }
}
