package dev.rambris.iff.codec;

import dev.rambris.iff.exceptions.IffParseException;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed 8SVX {@code VHDR} (Voice8Header) chunk.
 *
 * <p>Binary layout (all big-endian):
 * <pre>
 *   [4]  oneShotHiSamples   — samples in the high-octave one-shot part
 *   [4]  repeatHiSamples    — samples in the high-octave repeat part
 *   [4]  samplesPerHiCycle  — samples per cycle in the high octave (0 = not applicable)
 *   [2]  samplesPerSec      — sample rate in Hz
 *   [1]  octaves            — number of octaves of waveform data
 *   [1]  compression        — 0=none (PCM), 1=Fibonacci-delta
 *   [4]  volume             — playback volume 0..65536 (Amiga Fixed 16.16)
 * </pre>
 */
public record VhdrChunk(
        long oneShotHiSamples,
        long repeatHiSamples,
        long samplesPerHiCycle,
        int samplesPerSec,
        int octaves,
        int compression,
        int volume
) {

    public static final int COMPRESSION_NONE      = 0;
    public static final int COMPRESSION_FIBONACCI = 1;

    /** Parses a {@code VHDR} chunk payload. */
    public static VhdrChunk parse(byte[] data) {
        if (data.length < 20) {
            throw new IffParseException("VHDR chunk too small: " + data.length + " bytes");
        }
        var buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);
        long oneShotHiSamples  = buf.getInt() & 0xFFFFFFFFL;
        long repeatHiSamples   = buf.getInt() & 0xFFFFFFFFL;
        long samplesPerHiCycle = buf.getInt() & 0xFFFFFFFFL;
        int samplesPerSec      = buf.getShort() & 0xFFFF;
        int octaves            = buf.get() & 0xFF;
        int compression        = buf.get() & 0xFF;
        int volume             = buf.getInt();
        return new VhdrChunk(oneShotHiSamples, repeatHiSamples, samplesPerHiCycle,
                samplesPerSec, octaves, compression, volume);
    }

    /** Encodes this record to its 20-byte binary representation. */
    public byte[] encode() {
        var buf = ByteBuffer.allocate(20).order(ByteOrder.BIG_ENDIAN);
        buf.putInt((int) oneShotHiSamples);
        buf.putInt((int) repeatHiSamples);
        buf.putInt((int) samplesPerHiCycle);
        buf.putShort((short) samplesPerSec);
        buf.put((byte) octaves);
        buf.put((byte) compression);
        buf.putInt(volume);
        return buf.array();
    }
}
