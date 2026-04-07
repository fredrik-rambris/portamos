package dev.rambris.iff.codec;

import dev.rambris.iff.IffReader;
import dev.rambris.iff.IffWriter;
import dev.rambris.iff.IlbmId;
import dev.rambris.iff.exceptions.IffParseException;
import dev.rambris.iff.exceptions.IffReadException;
import dev.rambris.iff.exceptions.IffWriteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Codec for the IFF ILBM (Interleaved Bitmap) image format.
 *
 * <p>Supported chunks: {@code BMHD}, {@code CMAP}, {@code CAMG}, {@code BODY}.
 * Supported decompression: none and ByteRun1. Unknown chunks are skipped.
 *
 * <p>All methods are static.
 */
public class IlbmCodec {

    private IlbmCodec() {}

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses an IFF ILBM file from {@code path}.
     *
     * @throws IffReadException  if the file cannot be read
     * @throws IffParseException if the data is not a valid ILBM file
     */
    public static IlbmImage read(Path path) {
        return read(readBytes(path));
    }

    /**
     * Parses an IFF ILBM file from {@code data}.
     *
     * @throws IffParseException if the data is not a valid ILBM file
     */
    public static IlbmImage read(byte[] data) {
        var state = new IlbmState();

        var reader = new IffReader()
                .on(IlbmId.BMHD, (id, d) -> state.bmhd = BmhdChunk.parse(d))
                .on(IlbmId.CMAP, (id, d) -> state.palette = parseCmap(d))
                .on(IlbmId.CAMG, (id, d) -> state.camgMode = parseCamg(d))
                .on(IlbmId.BODY, (id, d) -> {
                    if (state.bmhd == null) {
                        throw new IffParseException("BODY chunk encountered before BMHD");
                    }
                    state.body = decompressBody(d, state.bmhd);
                });

        var formType = reader.read(data);
        if (!"ILBM".equals(formType)) {
            throw new IffParseException("Not an ILBM file: FORM type is '" + formType + "'");
        }
        if (state.bmhd == null) {
            throw new IffParseException("Missing required BMHD chunk");
        }

        return new IlbmImage(state.bmhd, state.palette, state.camgMode, state.body);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Encodes {@code image} as an IFF ILBM byte array.
     *
     * <p>Pass {@link IlbmOptions#COMPRESSION_BYTERUN1} to compress the {@code BODY}
     * chunk. Without any options the body is written uncompressed.
     *
     * <pre>{@code
     * byte[] compressed = IlbmCodec.write(image, IlbmOptions.COMPRESSION_BYTERUN1);
     * byte[] raw        = IlbmCodec.write(image);
     * }</pre>
     */
    public static byte[] write(IlbmImage image, IlbmOptions... options) {
        boolean useByteRun1 = Arrays.asList(options).contains(IlbmOptions.COMPRESSION_BYTERUN1);

        var bmhd = image.bmhd();
        int compression = useByteRun1
                ? BmhdChunk.COMPRESSION_BYTERUN1
                : BmhdChunk.COMPRESSION_NONE;

        var writeBmhd = new BmhdChunk(
                bmhd.width(), bmhd.height(), bmhd.x(), bmhd.y(),
                bmhd.planes(), bmhd.masking(), compression,
                bmhd.transparentColor(), bmhd.xAspect(), bmhd.yAspect(),
                bmhd.pageWidth(), bmhd.pageHeight());

        byte[] bodyBytes = (image.body() != null && useByteRun1)
                ? compressByteRun1(image.body(), writeBmhd)
                : (image.body() != null ? image.body() : new byte[0]);

        var writer = new IffWriter()
                .writeChunk(IlbmId.BMHD, writeBmhd.encode());

        if (image.palette() != null) {
            writer.writeChunk(IlbmId.CMAP, encodeCmap(image.palette()));
        }
        if (image.camgMode() != 0) {
            writer.writeChunk(IlbmId.CAMG, encodeCamg(image.camgMode()));
        }
        writer.writeChunk(IlbmId.BODY, bodyBytes);

        return writer.toForm(IlbmId.ILBM);
    }

    /**
     * Encodes {@code image} as an IFF ILBM file at {@code path}.
     *
     * @throws IffWriteException if the file cannot be written
     */
    public static void write(IlbmImage image, Path path, IlbmOptions... options) {
        try {
            Files.write(path, write(image, options));
        } catch (IOException e) {
            throw new IffWriteException("Cannot write ILBM file: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Public compression / decompression utilities
    // -------------------------------------------------------------------------

    /**
     * Compresses interleaved planar data using ByteRun1 (PackBits).
     *
     * <p>The input must be decompressed body data as produced by {@link #read} —
     * i.e., rows in order, each row containing {@code bmhd.planes()} plane strips
     * of {@code rowBytes} bytes each.
     *
     * <p>Compression is applied independently per plane strip per row, which allows
     * random-access by scan line and matches the format expected by AMOS and IFF readers.
     *
     * <p>Algorithm per strip: scan for runs of ≥ 3 identical bytes and emit them as
     * replicate codes; everything else is emitted as literal runs (max 128 bytes each).
     * Control byte semantics:
     * <ul>
     *   <li>{@code n ≥ 0}: copy next {@code n+1} bytes literally</li>
     *   <li>{@code n < 0} and {@code n ≠ -128}: replicate next byte {@code -n+1} times</li>
     * </ul>
     *
     * @param planar decompressed interleaved planar body bytes
     * @param bmhd   bitmap header supplying width, height, and plane count
     * @return ByteRun1-compressed bytes
     */
    public static byte[] compressByteRun1(byte[] planar, BmhdChunk bmhd) {
        int rowBytes  = ((bmhd.width() + 15) / 16) * 2;
        int rowsTotal = bmhd.height() * bmhd.planes();
        // Worst-case ByteRun1 output: every byte preceded by a literal-count byte
        var out = new ByteArrayOutputStream(planar.length + planar.length / 128 + rowsTotal);

        for (int row = 0; row < rowsTotal; row++) {
            encodeStrip(planar, row * rowBytes, rowBytes, out);
        }

        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static byte[] readBytes(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new IffReadException("Cannot read ILBM file: " + path, e);
        }
    }

    private static byte[] decompressBody(byte[] data, BmhdChunk bmhd) {
        return switch (bmhd.compression()) {
            case BmhdChunk.COMPRESSION_NONE     -> data;
            case BmhdChunk.COMPRESSION_BYTERUN1 -> decompressByteRun1(data, bmhd);
            default -> throw new IffParseException(
                    "Unsupported BODY compression: " + bmhd.compression());
        };
    }

    /**
     * Decompresses ByteRun1 (PackBits) data.
     *
     * <p>Control byte {@code n}: {@code n >= 0} → copy next {@code n+1} bytes literally;
     * {@code n < 0} and {@code n != -128} → replicate next byte {@code -n+1} times;
     * {@code n == -128} → no-op.
     */
    private static byte[] decompressByteRun1(byte[] compressed, BmhdChunk bmhd) {
        int rowBytes     = ((bmhd.width() + 15) / 16) * 2;
        int expectedSize = bmhd.height() * bmhd.planes() * rowBytes;
        var out = new ByteArrayOutputStream(expectedSize);
        int i = 0;
        while (i < compressed.length) {
            int n = compressed[i++];
            if (n >= 0) {
                int count = n + 1;
                out.write(compressed, i, count);
                i += count;
            } else if (n != -128) {
                int count = -n + 1;
                byte b = compressed[i++];
                for (int j = 0; j < count; j++) out.write(b);
            }
            // n == -128: no-op
        }
        return out.toByteArray();
    }

    /**
     * Encodes one plane strip (rowBytes bytes starting at {@code start}) into {@code out}.
     *
     * <p>Strategy: emit a replicate run whenever 3+ consecutive identical bytes are
     * seen (maximum 128); otherwise accumulate literal bytes up to 128 at a time.
     * A replicate run always terminates an in-progress literal run, so the encoder
     * never needs to back-patch.
     */
    private static void encodeStrip(byte[] data, int start, int len, ByteArrayOutputStream out) {
        int end = start + len;
        int pos = start;

        while (pos < end) {
            // --- Replicate run: 3+ identical bytes ---
            if (pos + 2 < end
                    && data[pos] == data[pos + 1]
                    && data[pos + 1] == data[pos + 2]) {

                int runLen = 3;
                while (runLen < 128 && pos + runLen < end && data[pos + runLen] == data[pos]) {
                    runLen++;
                }
                // Control byte: -(runLen - 1)  →  decoder reproduces runLen copies
                out.write(-(runLen - 1) & 0xFF);
                out.write(data[pos] & 0xFF);
                pos += runLen;

            } else {
                // --- Literal run: collect until a replicate opportunity ---
                int litStart = pos;
                while (pos < end) {
                    if (pos + 2 < end
                            && data[pos] == data[pos + 1]
                            && data[pos + 1] == data[pos + 2]) {
                        break; // upcoming replicate run — stop literal here
                    }
                    pos++;
                    if (pos - litStart == 128) break; // max 128 bytes per literal run
                }
                int litLen = pos - litStart;
                // Control byte: litLen - 1  →  decoder copies litLen bytes
                out.write(litLen - 1);
                out.write(data, litStart, litLen);
            }
        }
    }

    private static int[] parseCmap(byte[] data) {
        int count = data.length / 3;
        int[] palette = new int[count];
        for (int i = 0; i < count; i++) {
            palette[i] = ((data[i * 3]     & 0xFF) << 16)
                       | ((data[i * 3 + 1] & 0xFF) << 8)
                       |  (data[i * 3 + 2] & 0xFF);
        }
        return palette;
    }

    private static byte[] encodeCmap(int[] palette) {
        byte[] cmap = new byte[palette.length * 3];
        for (int i = 0; i < palette.length; i++) {
            cmap[i * 3]     = (byte)((palette[i] >> 16) & 0xFF);
            cmap[i * 3 + 1] = (byte)((palette[i] >> 8)  & 0xFF);
            cmap[i * 3 + 2] = (byte)( palette[i]        & 0xFF);
        }
        return cmap;
    }

    private static int parseCamg(byte[] data) {
        if (data.length < 4) {
            throw new IffParseException("CAMG chunk too small: " + data.length + " bytes");
        }
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static byte[] encodeCamg(int mode) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(mode).array();
    }

    // Mutable accumulator captured by lambdas during a single read() call.
    private static class IlbmState {
        BmhdChunk bmhd;
        int[] palette;
        int camgMode = 0;
        byte[] body;
    }
}
