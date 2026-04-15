/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

import dev.rambris.iff.IffReader;
import dev.rambris.iff.IffWriter;
import dev.rambris.iff.Svx8Id;
import dev.rambris.iff.exceptions.IffParseException;
import dev.rambris.iff.exceptions.IffReadException;
import dev.rambris.iff.exceptions.IffWriteException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Codec for the IFF 8SVX (8-bit Sampled Voice) audio format.
 *
 * <h3>Stereo</h3>
 * <p>Stereo is indicated by a {@code CHAN} chunk with value {@link Svx8Sound#CHAN_STEREO}.
 * The single {@code BODY} chunk then contains the LEFT block followed by the RIGHT block
 * (equal length), exactly as specified by the 8SVX standard. The codec does <em>not</em>
 * interleave or split the data; {@link Svx8Sound#pcmData()} is the raw BODY bytes.
 *
 * <h3>Compression</h3>
 * <p>Two schemes are supported:
 * <ul>
 *   <li>{@link VhdrChunk#COMPRESSION_NONE} — raw signed 8-bit PCM (no transformation)</li>
 *   <li>{@link VhdrChunk#COMPRESSION_FIBONACCI} — Fibonacci-delta (sCmpFibDelta from
 *       Appendix C of the 8SVX spec); decompressed automatically on read and optionally
 *       applied on write via {@link Svx8Options#COMPRESSION_FIBONACCI}</li>
 * </ul>
 *
 * <p>{@link Svx8Sound#pcmData()} is always decompressed. The original compression
 * scheme is preserved in {@link VhdrChunk#compression()} for informational purposes.
 *
 * <h3>Constraints</h3>
 * <ul>
 *   <li>Exactly one {@code BODY} chunk is expected; a second one is an error.</li>
 *   <li>{@code VHDR} must appear before {@code BODY}.</li>
 * </ul>
 *
 * <p>All methods are static.
 */
public class Svx8Codec {

    private Svx8Codec() {}

    // -------------------------------------------------------------------------
    // Fibonacci-delta table (from 8SVX spec Appendix C)
    // -------------------------------------------------------------------------

    /** Signed delta values indexed by 4-bit code (0..15). */
    static final int[] CODE_TO_DELTA = {-34, -21, -13, -8, -5, -3, -2, -1, 0, 1, 2, 3, 5, 8, 13, 21};

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Parses an IFF 8SVX file from {@code path}.
     *
     * @throws IffReadException  if the file cannot be read
     * @throws IffParseException if the data is not a valid 8SVX file
     */
    public static Svx8Sound read(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IffReadException("Cannot read 8SVX file: " + path, e);
        }
    }

    /**
     * Parses an IFF 8SVX file from {@code data}.
     *
     * @throws IffParseException if the data is not a valid 8SVX file
     */
    public static Svx8Sound read(byte[] data) {
        var state = new Svx8State();

        var reader = new IffReader()
                .on(Svx8Id.VHDR, (id, d) -> state.vhdr = VhdrChunk.parse(d))
                .on(Svx8Id.CHAN, (id, d) -> state.chan = parseChan(d))
                .on(Svx8Id.BODY, (id, d) -> {
                    if (state.bodyPresent) {
                        throw new IffParseException("Duplicate BODY chunk in 8SVX file");
                    }
                    if (state.vhdr == null) {
                        throw new IffParseException("BODY chunk encountered before VHDR");
                    }
                    state.bodyPresent = true;
                    state.body = decompressBody(d, state.vhdr);
                });

        var formType = reader.read(data);
        if (!"8SVX".equals(formType)) {
            throw new IffParseException("Not an 8SVX file: FORM type is '" + formType + "'");
        }
        if (state.vhdr == null) {
            throw new IffParseException("Missing required VHDR chunk");
        }
        if (!state.bodyPresent) {
            throw new IffParseException("Missing required BODY chunk");
        }

        return new Svx8Sound(state.vhdr, state.chan, state.body);
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    /**
     * Encodes {@code sound} as an IFF 8SVX byte array.
     *
     * <p>Pass {@link Svx8Options#COMPRESSION_FIBONACCI} to compress the {@code BODY}
     * chunk with Fibonacci-delta encoding. Without any options the body is written
     * as raw uncompressed PCM.
     *
     * <pre>{@code
     * byte[] compressed = Svx8Codec.write(sound, Svx8Options.COMPRESSION_FIBONACCI);
     * byte[] raw        = Svx8Codec.write(sound);
     * }</pre>
     */
    public static byte[] write(Svx8Sound sound, Svx8Options... options) {
        var useFib = Arrays.asList(options).contains(Svx8Options.COMPRESSION_FIBONACCI);

        int compression = useFib
                ? VhdrChunk.COMPRESSION_FIBONACCI
                : VhdrChunk.COMPRESSION_NONE;

        // Write a fresh VHDR with the actual compression we are using.
        var src = sound.vhdr();
        var writeVhdr = new VhdrChunk(src.oneShotHiSamples(), src.repeatHiSamples(),
                src.samplesPerHiCycle(), src.samplesPerSec(), src.octaves(),
                compression, src.volume());

        var bodyBytes = useFib
                ? compressFibDelta(sound.pcmData())
                : sound.pcmData();

        var writer = new IffWriter()
                .writeChunk(Svx8Id.VHDR, writeVhdr.encode());

        if (sound.chan() != Svx8Sound.CHAN_MONO) {
            writer.writeChunk(Svx8Id.CHAN, encodeChan(sound.chan()));
        }

        writer.writeChunk(Svx8Id.BODY, bodyBytes);

        return writer.toForm(Svx8Id.SVX8);
    }

    /**
     * Encodes {@code sound} as an IFF 8SVX file at {@code path}.
     *
     * @throws IffWriteException if the file cannot be written
     */
    public static void write(Svx8Sound sound, Path path, Svx8Options... options) {
        try {
            Files.write(path, write(sound, options));
        } catch (IOException e) {
            throw new IffWriteException("Cannot write 8SVX file: " + path, e);
        }
    }

    // -------------------------------------------------------------------------
    // Public compression utilities
    // -------------------------------------------------------------------------

    /**
     * Decompresses Fibonacci-delta encoded 8SVX BODY data.
     *
     * <p>Format: {@code source[0]} = pad byte (ignored); {@code source[1]} = initial
     * sample value {@code x}; {@code source[2..n-1]} = {@code n-2} bytes each holding
     * two 4-bit codes (high nibble first). Output length = {@code 2*(n-2)}.
     *
     * <p>Each code is an index into {@link #CODE_TO_DELTA}; the decoded delta is
     * added to the running value {@code x} to produce each output sample.
     *
     * @param source Fibonacci-delta compressed bytes
     * @return decompressed signed 8-bit PCM samples
     * @throws IffParseException if {@code source} is too small
     */
    public static byte[] decompressFibDelta(byte[] source) {
        if (source.length < 2) {
            throw new IffParseException(
                    "Fibonacci delta: source buffer too small (" + source.length + " bytes)");
        }
        int n          = source.length;
        int numSamples = 2 * (n - 2);
        var dest    = new byte[numSamples];
        byte x         = source[1]; // initial value

        for (int i = 0; i < numSamples; i++) {
            int pair   = source[2 + (i >> 1)] & 0xFF;
            int nibble = ((i & 1) == 0) ? (pair >> 4) & 0xF : pair & 0xF;
            x         += CODE_TO_DELTA[nibble]; // wraps as signed byte
            dest[i]    = x;
        }
        return dest;
    }

    /**
     * Compresses PCM data using Fibonacci-delta encoding.
     *
     * <p>Output format: pad byte ({@code 0x00}), initial value ({@code pcm[0]}),
     * followed by {@code ceil(N / 2)} bytes packing two 4-bit codes each.
     * Total output size = {@code 2 + ceil(N / 2)}.
     *
     * <p>For each sample, the algorithm finds the code whose delta value best
     * approximates the difference from the current running value, always tracking
     * the quantised (not ideal) running value to prevent drift accumulation.
     *
     * @param pcm signed 8-bit PCM samples to compress
     * @return Fibonacci-delta compressed bytes
     */
    public static byte[] compressFibDelta(byte[] pcm) {
        if (pcm.length == 0) {
            return new byte[]{0, 0};
        }
        int N        = pcm.length;
        int dataBytes = (N + 1) / 2;       // ceil(N/2) encoded bytes
        var comp  = new byte[2 + dataBytes];
        comp[0]      = 0;        // pad byte
        comp[1]      = pcm[0];   // initial value
        byte x       = pcm[0];   // running value, mirrors decoder

        for (int i = 0; i < N; i++) {
            int desired  = (int) pcm[i] - (int) x; // signed arithmetic difference
            int bestCode = bestCode(desired);
            x           += CODE_TO_DELTA[bestCode]; // update running value (wraps as byte)

            int byteIdx = 2 + (i >> 1);
            if ((i & 1) == 0) {
                comp[byteIdx] = (byte) ((bestCode & 0xF) << 4); // high nibble
            } else {
                comp[byteIdx] |= (byte)  (bestCode & 0xF);      // low nibble
            }
        }
        return comp;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    private static byte[] decompressBody(byte[] data, VhdrChunk vhdr) {
        return switch (vhdr.compression()) {
            case VhdrChunk.COMPRESSION_NONE      -> data;
            case VhdrChunk.COMPRESSION_FIBONACCI -> decompressFibDelta(data);
            default -> throw new IffParseException(
                    "Unsupported 8SVX compression: " + vhdr.compression());
        };
    }

    /** Returns the code whose {@link #CODE_TO_DELTA} value is closest to {@code desired}. */
    private static int bestCode(int desired) {
        int best      = 8; // delta = 0
        int bestError = Math.abs(desired);
        for (int c = 0; c < 16; c++) {
            int err = Math.abs(desired - CODE_TO_DELTA[c]);
            if (err < bestError) {
                bestError = err;
                best      = c;
                if (bestError == 0) break;
            }
        }
        return best;
    }

    private static int parseChan(byte[] data) {
        if (data.length < 4) {
            throw new IffParseException("CHAN chunk too small: " + data.length + " bytes");
        }
        return ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN).getInt();
    }

    private static byte[] encodeChan(int chanValue) {
        return ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN).putInt(chanValue).array();
    }

    // Mutable accumulator captured by lambdas during a single read() call.
    private static class Svx8State {
        VhdrChunk vhdr;
        int       chan        = Svx8Sound.CHAN_MONO;
        byte[]    body;
        boolean   bodyPresent = false;
    }
}
