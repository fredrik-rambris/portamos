package dev.rambris.amigaamos.bank;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Compresses a chunky pixel array into AMOS Professional Pac.Pic. format.
 *
 * <p>The algorithm is a faithful Java port of the 68k compactor in
 * {@code reference/AMOSProfessional/extensions/+Compact.s}:
 *
 * <h3>Lump-height optimisation</h3>
 * The image is divided vertically into equal-height "lumps".  Within each lump
 * bytes are stored column-major (column 0 all rows, column 1 all rows, …).
 * Candidate lump heights are tried: {@code {1,2,3,4,5,6,7,8,12,16,24,32,48,64}}.
 * Only values that evenly divide the image height are eligible.  The one that
 * produces the smallest compressed output is used.
 *
 * <h3>Two-pass RLE</h3>
 * <ol>
 *   <li><b>Pass 1</b> — picture bytes → PICDATA + raw RLEDATA bitstream<br>
 *       Walk in order: plane → lump → column → row.  For each byte B:
 *       if B ≠ previous → emit B to PICDATA, set bit 1 in RLEDATA;
 *       else → set bit 0 (skip).  RLEDATA is packed 8 bits per byte.</li>
 *   <li><b>Pass 2</b> — RLEDATA bytes → RLEDATA values + POINTS bitstream<br>
 *       Apply the same byte-level RLE to the RLEDATA byte stream:
 *       distinct consecutive values go to the RLEDATA output;
 *       a POINTS bit records whether each RLEDATA byte was new (1) or
 *       repeated (0).</li>
 * </ol>
 *
 * <h3>Stream layout in the output byte array</h3>
 * <pre>
 *   [0..23]                  header  (magic, geometry, rleOff, ptsOff)
 *   [24 .. ptsOff-1]         PICDATA  (dummy 0x00 + distinct picture bytes)
 *   [ptsOff .. rleOff-1]     POINTS   (raw bitstream; ptsAreaSize = interSize/8+2)
 *   [rleOff .. end]          RLEDATA  (dummy 0x00 + distinct RLEDATA bytes)
 * </pre>
 * Note: {@code rleOff > ptsOff} (RLEDATA comes after POINTS in the file).
 * The header stores {@code rleOff} at struct offset +16 and {@code ptsOff} at
 * struct offset +20, matching what {@link PacPicDecoder} reads.
 */
public class PacPicEncoder {

    /** Lump-height candidates, matching TSize table in the 68k source. */
    private static final int[] LUMP_HEIGHTS = {1, 2, 3, 4, 5, 6, 7, 8, 12, 16, 24, 32, 48, 64};

    /**
     * Compresses a palette-index pixel array to Pac.Pic. format.
     *
     * @param pixels  palette-index array {@code [y][x]}
     * @param srcX    source X position in pixels; <strong>must be divisible by 8</strong>
     * @param srcY    source Y position in pixels
     * @param planes  number of bitplanes (1–5 typical)
     * @return full Pac.Pic byte array starting with magic {@code 0x06071963}
     */
    public static byte[] compress(int[][] pixels, int srcX, int srcY, int planes) {
        int height = pixels.length;
        int wPx    = height > 0 ? pixels[0].length : 0;

        // Width in bytes — one byte per 8 pixels, no further alignment needed for Pac.Pic
        int wBytes = (wPx + 7) / 8;

        // Convert chunky palette indices → row-major planar buffers
        byte[][] planeBufs = buildPlaneBufs(pixels, planes, height, wPx, wBytes);

        // Find the lump height that gives the smallest compressed size
        int bestLumpH = 1;
        int bestSize  = Integer.MAX_VALUE;
        for (int lh : LUMP_HEIGHTS) {
            if (lh > height) break;
            if (height % lh != 0) continue;
            int sz = estimateSize(planeBufs, wBytes, height, planes, lh);
            if (sz < bestSize) { bestSize = sz; bestLumpH = lh; }
        }

        return pack(planeBufs, srcX, srcY, planes, height, wBytes, bestLumpH);
    }

    // -------------------------------------------------------------------------
    // Chunky → planar conversion
    // -------------------------------------------------------------------------

    private static byte[][] buildPlaneBufs(int[][] pixels, int planes,
                                           int height, int wPx, int wBytes) {
        byte[][] bufs = new byte[planes][height * wBytes];
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < wPx; x++) {
                int idx     = pixels[y][x];
                int byteIdx = y * wBytes + (x >> 3);
                int mask    = 0x80 >> (x & 7);
                for (int p = 0; p < planes; p++) {
                    if ((idx & (1 << p)) != 0) bufs[p][byteIdx] |= (byte) mask;
                }
            }
        }
        return bufs;
    }

    // -------------------------------------------------------------------------
    // Size estimation (dry run, no output allocation)
    // -------------------------------------------------------------------------

    private static int estimateSize(byte[][] planeBufs, int wBytes,
                                    int height, int planes, int lumpH) {
        int lumps    = height / lumpH;
        int N        = planes * height * wBytes;   // total picture bytes
        int interSz  = N / 8 + 2;                  // intermediate buffer size
        byte[] inter = new byte[interSz];

        // Pass 1 simulation
        int picCount    = 1;   // starts at 1 for dummy byte
        int prevPic     = 0;
        int rlebit      = 7;
        int rleByteIdx  = 0;

        for (int plane = 0; plane < planes; plane++) {
            byte[] buf  = planeBufs[plane];
            int lumpRow = 0;
            for (int j = 0; j < lumps; j++) {
                for (int col = 0; col < wBytes; col++) {
                    for (int row = 0; row < lumpH; row++) {
                        int b = buf[(lumpRow + row) * wBytes + col] & 0xFF;
                        if (b != prevPic) {
                            prevPic = b;
                            picCount++;
                            inter[rleByteIdx] |= (1 << rlebit);
                        }
                        if (--rlebit < 0) { rlebit = 7; rleByteIdx++; }
                    }
                }
                lumpRow += lumpH;
            }
        }

        // Pass 2 simulation
        int rleCount  = 1;   // starts at 1 for dummy byte
        int prevRle   = 0;
        for (int i = 0; i < interSz; i++) {
            int r = inter[i] & 0xFF;
            if (r != prevRle) { prevRle = r; rleCount++; }
        }

        int ptsAreaSz = interSz / 8 + 2;
        int rawTotal  = 24 + picCount + ptsAreaSz + rleCount;
        return (rawTotal + 3) & ~1;   // round up to even (matches assembly)
    }

    // -------------------------------------------------------------------------
    // Actual compression
    // -------------------------------------------------------------------------

    private static byte[] pack(byte[][] planeBufs, int srcX, int srcY,
                               int planes, int height, int wBytes, int lumpH) {
        int lumps    = height / lumpH;
        int N        = planes * height * wBytes;
        int interSz  = N / 8 + 2;
        byte[] inter = new byte[interSz];

        // ---- Pass 1: picture bytes → PICDATA + raw RLEDATA intermediate ----
        // PICDATA byte 0 = dummy initial value (0), matching initial prevPic=0
        byte[] picdata     = new byte[N + 1]; // worst case: every byte is new
        int    picLen      = 1;               // picdata[0] = 0 already (array init)
        int    prevPic     = 0;
        int    rlebit      = 7;
        int    rleByteIdx  = 0;

        for (int plane = 0; plane < planes; plane++) {
            byte[] buf  = planeBufs[plane];
            int lumpRow = 0;
            for (int j = 0; j < lumps; j++) {
                for (int col = 0; col < wBytes; col++) {
                    for (int row = 0; row < lumpH; row++) {
                        int b = buf[(lumpRow + row) * wBytes + col] & 0xFF;
                        if (b != prevPic) {
                            prevPic = b;
                            picdata[picLen++] = (byte) b;
                            inter[rleByteIdx] |= (1 << rlebit);
                        }
                        if (--rlebit < 0) { rlebit = 7; rleByteIdx++; }
                    }
                }
                lumpRow += lumpH;
            }
        }

        // ---- Pass 2: RLEDATA bytes → RLEDATA values + POINTS bitstream ----
        // RLEDATA[0] = dummy initial value (0), matching initial prevRle=0
        byte[] rledata    = new byte[interSz + 1]; // worst case
        int    rleLen     = 1;                       // rledata[0] = 0 already
        int    ptsAreaSz  = interSz / 8 + 2;
        byte[] pts        = new byte[ptsAreaSz];     // all zeros = no advance by default
        int    prevRle    = 0;
        int    ptsbit     = 7;
        int    ptsByteIdx = 0;

        for (int i = 0; i < interSz; i++) {
            int r = inter[i] & 0xFF;
            if (r != prevRle) {
                prevRle = r;
                rledata[rleLen++] = (byte) r;
                pts[ptsByteIdx] |= (1 << ptsbit);
            }
            if (--ptsbit < 0) { ptsbit = 7; ptsByteIdx++; }
        }

        // ---- Assemble output ----
        int ptsOff   = 24 + picLen;
        int rleOff   = ptsOff + ptsAreaSz;
        int rawTotal = rleOff + rleLen;
        int total    = (rawTotal + 3) & ~1;   // round up to even

        ByteBuffer out = ByteBuffer.allocate(total).order(ByteOrder.BIG_ENDIAN);
        out.putInt(0x06071963);              // magic
        out.putShort((short) (srcX / 8));   // Pkdx: source X in bytes
        out.putShort((short) srcY);          // Pkdy: source Y in pixels
        out.putShort((short) wBytes);        // Pktx: width in bytes
        out.putShort((short) lumps);         // Pkty: number of lumps
        out.putShort((short) lumpH);         // Pktcar: lines per lump
        out.putShort((short) planes);        // Pkplan: bitplanes
        out.putInt(rleOff);                  // rleOff  (at struct+16, read first by decompressor)
        out.putInt(ptsOff);                  // ptsOff  (at struct+20, read second by decompressor)
        out.put(picdata,  0, picLen);        // PICDATA at offset 24
        out.put(pts,      0, ptsAreaSz);     // POINTS  at ptsOff
        out.put(rledata,  0, rleLen);        // RLEDATA at rleOff
        // remaining bytes are zero (padding for even length)

        return out.array();
    }
}
