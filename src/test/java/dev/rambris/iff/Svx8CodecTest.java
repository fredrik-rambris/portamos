/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import dev.rambris.iff.codec.Svx8Codec;
import dev.rambris.iff.codec.Svx8Options;
import dev.rambris.iff.codec.Svx8Sound;
import dev.rambris.iff.codec.VhdrChunk;
import dev.rambris.iff.exceptions.IffParseException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class Svx8CodecTest {

    private static VhdrChunk pcmVhdr(int sampleCount) {
        return new VhdrChunk(sampleCount, 0, 0, 22050, 1, VhdrChunk.COMPRESSION_NONE, 65536);
    }

    // -------------------------------------------------------------------------
    // Mono round-trip (PCM)
    // -------------------------------------------------------------------------

    @Test
    void monoRoundTrip() {
        byte[] samples = {10, 20, 30, 40, 50, 60};
        var original = new Svx8Sound(pcmVhdr(samples.length), Svx8Sound.CHAN_MONO, samples);

        byte[] encoded = Svx8Codec.write(original);
        Svx8Sound decoded = Svx8Codec.read(encoded);

        assertEquals(Svx8Sound.CHAN_MONO, decoded.chan());
        assertFalse(decoded.stereo());
        assertEquals(22050, decoded.vhdr().samplesPerSec());
        assertArrayEquals(samples, decoded.pcmData());
    }

    @Test
    void mono_noChanChunkWritten() {
        var sound = new Svx8Sound(pcmVhdr(4), Svx8Sound.CHAN_MONO, new byte[]{1, 2, 3, 4});
        byte[] encoded = Svx8Codec.write(sound);

        // "CHAN" should not appear in the output
        assertFalse(containsId(encoded, "CHAN"),
                "CHAN chunk should not be written for mono sounds");
    }

    // -------------------------------------------------------------------------
    // Stereo round-trip (PCM)
    // -------------------------------------------------------------------------

    @Test
    void stereoRoundTrip_leftBlockThenRightBlock() {
        // Stereo: 6 bytes left + 6 bytes right, stored contiguously in BODY.
        byte[] left  = {1, 2, 3, 4, 5, 6};
        byte[] right = {7, 8, 9, 10, 11, 12};
        byte[] body  = new byte[12];
        System.arraycopy(left,  0, body, 0, 6);
        System.arraycopy(right, 0, body, 6, 6);

        var original = new Svx8Sound(pcmVhdr(6), Svx8Sound.CHAN_STEREO, body);

        byte[] encoded = Svx8Codec.write(original);
        Svx8Sound decoded = Svx8Codec.read(encoded);

        assertTrue(decoded.stereo());
        assertEquals(Svx8Sound.CHAN_STEREO, decoded.chan());
        assertArrayEquals(body, decoded.pcmData());
    }

    @Test
    void stereo_chanChunkWritten() {
        var body  = new byte[12];
        var sound = new Svx8Sound(pcmVhdr(6), Svx8Sound.CHAN_STEREO, body);
        byte[] encoded = Svx8Codec.write(sound);

        assertTrue(containsId(encoded, "CHAN"),
                "CHAN chunk should be written for stereo sounds");
    }

    @Test
    void leftChannelRoundTrip() {
        byte[] samples = {5, 10, 15};
        var sound = new Svx8Sound(pcmVhdr(3), Svx8Sound.CHAN_LEFT, samples);

        byte[] encoded = Svx8Codec.write(sound);
        Svx8Sound decoded = Svx8Codec.read(encoded);

        assertEquals(Svx8Sound.CHAN_LEFT, decoded.chan());
        assertFalse(decoded.stereo());
        assertArrayEquals(samples, decoded.pcmData());
    }

    // -------------------------------------------------------------------------
    // BODY constraints
    // -------------------------------------------------------------------------

    @Test
    void duplicateBody_throws() {
        var writer = new IffWriter()
                .writeChunk(Svx8Id.VHDR, pcmVhdr(2).encode())
                .writeChunk(Svx8Id.BODY, new byte[]{1, 2})
                .writeChunk(Svx8Id.BODY, new byte[]{3, 4});
        byte[] encoded = writer.toForm(Svx8Id.SVX8);

        assertThrows(IffParseException.class, () -> Svx8Codec.read(encoded));
    }

    @Test
    void missingVhdr_throws() {
        var writer = new IffWriter()
                .writeChunk(Svx8Id.BODY, new byte[]{1, 2, 3});
        byte[] encoded = writer.toForm(Svx8Id.SVX8);

        assertThrows(IffParseException.class, () -> Svx8Codec.read(encoded));
    }

    @Test
    void missingBody_throws() {
        var writer = new IffWriter()
                .writeChunk(Svx8Id.VHDR, pcmVhdr(0).encode());
        byte[] encoded = writer.toForm(Svx8Id.SVX8);

        assertThrows(IffParseException.class, () -> Svx8Codec.read(encoded));
    }

    @Test
    void notSvx8Form_throws() {
        var writer = new IffWriter()
                .writeChunk(Svx8Id.VHDR, pcmVhdr(0).encode())
                .writeChunk(Svx8Id.BODY, new byte[]{0});
        byte[] encoded = writer.toForm(IlbmId.ILBM); // wrong form type

        assertThrows(IffParseException.class, () -> Svx8Codec.read(encoded));
    }

    // -------------------------------------------------------------------------
    // Fibonacci delta decompression
    // -------------------------------------------------------------------------

    @Test
    void fibDelta_decompress_knownValues() {
        // From spec: initial x=10, one encoded byte 0x88
        // nibble 0 = 8 → delta=0 → x=10; nibble 1 = 8 → delta=0 → x=10
        byte[] source = {0, 10, (byte) 0x88};
        byte[] dest   = Svx8Codec.decompressFibDelta(source);
        assertArrayEquals(new byte[]{10, 10}, dest);
    }

    @Test
    void fibDelta_decompress_positiveDelta() {
        // initial x=0, encode nibble 9 (delta=1) twice → output {1,2}
        // 0x99 = high=9 (delta=1), low=9 (delta=1)
        byte[] source = {0, 0, (byte) 0x99};
        byte[] dest   = Svx8Codec.decompressFibDelta(source);
        assertArrayEquals(new byte[]{1, 2}, dest);
    }

    @Test
    void fibDelta_decompress_negativeDelta() {
        // initial x=20, nibble 7 (delta=-1) twice → output {19,18}
        // 0x77 = high=7, low=7
        byte[] source = {0, 20, (byte) 0x77};
        byte[] dest   = Svx8Codec.decompressFibDelta(source);
        assertArrayEquals(new byte[]{19, 18}, dest);
    }

    @Test
    void fibDelta_emptyPayload_returnsEmpty() {
        // 2 bytes: pad + initial only → 0 samples
        byte[] source = {0, 42};
        byte[] dest   = Svx8Codec.decompressFibDelta(source);
        assertEquals(0, dest.length);
    }

    // -------------------------------------------------------------------------
    // Fibonacci delta compression
    // -------------------------------------------------------------------------

    @Test
    void fibDelta_compress_thenDecompress_roundTrip() {
        byte[] pcm      = {0, 1, 2, 1, 0, -1, -2, -1, 0}; // smooth sine-like shape
        byte[] comp     = Svx8Codec.compressFibDelta(pcm);
        byte[] decompFull = Svx8Codec.decompressFibDelta(comp);

        // The decompressed output may be longer than pcm if pcm.length is odd;
        // compare only the first pcm.length samples.
        byte[] decomp = new byte[pcm.length];
        System.arraycopy(decompFull, 0, decomp, 0, pcm.length);

        // Fibonacci delta is lossy for large deltas but exact for delta=0 or small steps.
        // For this gentle waveform the round-trip should be exact (all deltas ≤ 2).
        assertArrayEquals(pcm, decomp);
    }

    @Test
    void fibDelta_compress_uniformSignal_isExact() {
        // All same value → all delta=0 → lossless
        byte[] pcm  = new byte[10];
        java.util.Arrays.fill(pcm, (byte) 42);
        byte[] comp = Svx8Codec.compressFibDelta(pcm);
        byte[] out  = Svx8Codec.decompressFibDelta(comp);
        for (int i = 0; i < pcm.length; i++) {
            assertEquals(pcm[i], out[i], "Sample " + i + " mismatch");
        }
    }

    @Test
    void fibDelta_compress_headerStructure() {
        byte[] pcm  = {5, 6, 7, 8};
        byte[] comp = Svx8Codec.compressFibDelta(pcm);

        assertEquals(0,    comp[0]); // pad byte
        assertEquals(5,    comp[1]); // initial value = pcm[0]
        assertEquals(4, comp.length); // 2 header + ceil(4/2) = 4
    }

    // -------------------------------------------------------------------------
    // Fibonacci compression via write/read round-trip
    // -------------------------------------------------------------------------

    @Test
    void fibDelta_writeRead_roundTrip_smooth() {
        byte[] pcm = {0, 1, 2, 3, 2, 1, 0, -1, -2, -3, -2, -1};
        var original = new Svx8Sound(pcmVhdr(pcm.length), Svx8Sound.CHAN_MONO, pcm);

        byte[] encoded = Svx8Codec.write(original, Svx8Options.COMPRESSION_FIBONACCI);
        Svx8Sound decoded = Svx8Codec.read(encoded);

        assertEquals(VhdrChunk.COMPRESSION_FIBONACCI, decoded.vhdr().compression());
        // Smooth signal: all deltas ≤ 3, all in the Fibonacci table exactly → lossless
        for (int i = 0; i < pcm.length; i++) {
            assertEquals(pcm[i], decoded.pcmData()[i], "Sample " + i + " mismatch");
        }
    }

    @Test
    void fibDelta_writeRead_roundTrip_silent() {
        byte[] pcm = new byte[20]; // all zeros
        var sound = new Svx8Sound(pcmVhdr(pcm.length), Svx8Sound.CHAN_MONO, pcm);

        byte[] encoded = Svx8Codec.write(sound, Svx8Options.COMPRESSION_FIBONACCI);
        Svx8Sound decoded = Svx8Codec.read(encoded);

        assertArrayEquals(pcm, decoded.pcmData());
    }

    @Test
    void fibDelta_write_setsCompressionInVhdr() {
        var sound = new Svx8Sound(pcmVhdr(4), Svx8Sound.CHAN_MONO, new byte[]{1, 2, 1, 2});
        byte[] encoded = Svx8Codec.write(sound, Svx8Options.COMPRESSION_FIBONACCI);
        Svx8Sound decoded = Svx8Codec.read(encoded);
        assertEquals(VhdrChunk.COMPRESSION_FIBONACCI, decoded.vhdr().compression());
    }

    @Test
    void pcm_write_setsCompressionZeroInVhdr() {
        // Even if the input vhdr says Fibonacci, writing without the option gives PCM.
        var fibVhdr = new VhdrChunk(4, 0, 0, 22050, 1, VhdrChunk.COMPRESSION_FIBONACCI, 65536);
        var sound = new Svx8Sound(fibVhdr, Svx8Sound.CHAN_MONO, new byte[]{1, 2, 3, 4});

        byte[] encoded = Svx8Codec.write(sound); // no option → PCM
        Svx8Sound decoded = Svx8Codec.read(encoded);
        assertEquals(VhdrChunk.COMPRESSION_NONE, decoded.vhdr().compression());
    }

    // -------------------------------------------------------------------------
    // Real stereo file
    // -------------------------------------------------------------------------

    @Test
    void stereoRealFile_readsCorrectly() throws IOException {
        Path path = Paths.get("src/test/resources/stereo.8svx");
        Svx8Sound sound = Svx8Codec.read(path);

        // Channel
        assertEquals(Svx8Sound.CHAN_STEREO, sound.chan());
        assertTrue(sound.stereo());

        // Sample rate from the file (8000 Hz, created by Sound Exchange)
        assertEquals(8000, sound.vhdr().samplesPerSec());

        // Uncompressed PCM
        assertEquals(VhdrChunk.COMPRESSION_NONE, sound.vhdr().compression());

        // BODY = 46986 bytes = 23493 left + 23493 right
        assertEquals(46986, sound.pcmData().length);
        assertEquals(23493, sound.leftChannel().length);
        assertEquals(23493, sound.rightChannel().length);
    }

    @Test
    void stereoRealFile_leftAndRightChannelsAreDifferent() throws IOException {
        Path path = Paths.get("src/test/resources/stereo.8svx");
        Svx8Sound sound = Svx8Codec.read(path);

        byte[] left  = sound.leftChannel();
        byte[] right = sound.rightChannel();

        // The two channels of a real stereo recording are not identical.
        assertFalse(java.util.Arrays.equals(left, right),
                "Left and right channel data should differ in a real stereo recording");
    }

    @Test
    void stereoRealFile_splitIsAtHalfway() throws IOException {
        Path path = Paths.get("src/test/resources/stereo.8svx");
        Svx8Sound sound = Svx8Codec.read(path);

        int half = sound.pcmData().length / 2;
        // leftChannel() returns pcmData[0..half-1]
        for (int i = 0; i < half; i++) {
            assertEquals(sound.pcmData()[i], sound.leftChannel()[i],
                    "Left channel mismatch at index " + i);
        }
        // rightChannel() returns pcmData[half..end]
        for (int i = 0; i < half; i++) {
            assertEquals(sound.pcmData()[half + i], sound.rightChannel()[i],
                    "Right channel mismatch at index " + i);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Returns true if the 4-character ASCII {@code id} appears anywhere in {@code data}. */
    private static boolean containsId(byte[] data, String id) {
        byte[] needle = id.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        outer:
        for (int i = 0; i <= data.length - 4; i++) {
            for (int j = 0; j < 4; j++) {
                if (data[i + j] != needle[j]) continue outer;
            }
            return true;
        }
        return false;
    }
}
