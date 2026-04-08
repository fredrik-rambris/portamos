package dev.rambris.iff.audio;

import dev.rambris.iff.codec.Svx8Codec;
import dev.rambris.iff.codec.Svx8Options;
import dev.rambris.iff.codec.Svx8Sound;
import dev.rambris.iff.codec.VhdrChunk;
import org.junit.jupiter.api.Test;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

class Svx8AudioSystemTest {

    @Test
    void reader_getAudioFileFormat_reportsSvx8Metadata() throws Exception {
        var samples = new byte[]{0, 1, 2, 3};
        var sound = new Svx8Sound(
                new VhdrChunk(samples.length, 2, 0, 11025, 2, VhdrChunk.COMPRESSION_NONE, 12345),
                Svx8Sound.CHAN_RIGHT,
                samples
        );
        var encoded = Svx8Codec.write(sound, Svx8Options.COMPRESSION_FIBONACCI);

        var reader = new Svx8AudioFileReader();
        var format = reader.getAudioFileFormat(new ByteArrayInputStream(encoded));

        assertEquals(Svx8AudioFileTypes.SVX8.toString(), format.getType().toString());
        assertEquals(11025.0f, format.getFormat().getSampleRate());
        assertEquals(1, format.getFormat().getChannels());
        assertEquals(samples.length, format.getFrameLength());
        assertEquals(Svx8Sound.CHAN_RIGHT, format.getFormat().properties().get(Svx8AudioSupport.PROP_CHAN));
        assertEquals(2, format.getFormat().properties().get(Svx8AudioSupport.PROP_OCTAVES));
        assertEquals(12345, format.getFormat().properties().get(Svx8AudioSupport.PROP_VOLUME));
        assertEquals(
                VhdrChunk.COMPRESSION_FIBONACCI,
                format.getFormat().properties().get(Svx8AudioSupport.PROP_COMPRESSION)
        );
    }

    @Test
    void reader_getAudioInputStream_interleavesStereoBody() throws Exception {
        var left = new byte[]{1, 2, 3};
        var right = new byte[]{9, 8, 7};
        var body = new byte[]{1, 2, 3, 9, 8, 7};
        var sound = new Svx8Sound(pcmVhdr(left.length, 8000), Svx8Sound.CHAN_STEREO, body);
        var encoded = Svx8Codec.write(sound);

        var reader = new Svx8AudioFileReader();
        try (var stream = reader.getAudioInputStream(new ByteArrayInputStream(encoded))) {
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, stream.getFormat().getEncoding());
            assertEquals(2, stream.getFormat().getChannels());
            assertEquals(3, stream.getFrameLength());
            assertArrayEquals(new byte[]{1, 9, 2, 8, 3, 7}, stream.readAllBytes());
        }
    }

    @Test
    void writer_write_monoPcm_producesReadable8svx() throws Exception {
        var samples = new byte[]{10, 20, 30, 40};
        var writer = new Svx8AudioFileWriter();
        var baos = new ByteArrayOutputStream();

        try (var stream = pcmStream(samples, 8000, 1, Map.of())) {
            var written = writer.write(stream, Svx8AudioFileTypes.SVX8, baos);
            assertTrue(written > 0);
        }

        var decoded = Svx8Codec.read(baos.toByteArray());
        assertEquals(Svx8Sound.CHAN_MONO, decoded.chan());
        assertEquals(8000, decoded.vhdr().samplesPerSec());
        assertArrayEquals(samples, decoded.pcmData());
    }

    @Test
    void writer_write_stereoInterleavedPcm_deinterleavesTo8svxBody() throws Exception {
        var interleaved = new byte[]{1, 9, 2, 8, 3, 7};
        var writer = new Svx8AudioFileWriter();
        var baos = new ByteArrayOutputStream();

        try (var stream = pcmStream(interleaved, 16000, 2, Map.of())) {
            writer.write(stream, Svx8AudioFileTypes.SVX8, baos);
        }

        var decoded = Svx8Codec.read(baos.toByteArray());
        assertTrue(decoded.stereo());
        assertEquals(16000, decoded.vhdr().samplesPerSec());
        assertArrayEquals(new byte[]{1, 2, 3}, decoded.leftChannel());
        assertArrayEquals(new byte[]{9, 8, 7}, decoded.rightChannel());
    }

    @Test
    void writer_write_preservesSvx8PropertiesWhenPresent() throws Exception {
        var samples = new byte[]{0, 1, 2, 3};
        var properties = Map.<String, Object>of(
                Svx8AudioSupport.PROP_CHAN, Svx8Sound.CHAN_RIGHT,
                Svx8AudioSupport.PROP_OCTAVES, 3,
                Svx8AudioSupport.PROP_VOLUME, 22222,
                Svx8AudioSupport.PROP_COMPRESSION, VhdrChunk.COMPRESSION_FIBONACCI,
                Svx8AudioSupport.PROP_REPEAT_HI_SAMPLES, 5L,
                Svx8AudioSupport.PROP_SAMPLES_PER_HI_CYCLE, 7L
        );
        var writer = new Svx8AudioFileWriter();
        var baos = new ByteArrayOutputStream();

        try (var stream = pcmStream(samples, 11025, 1, properties)) {
            writer.write(stream, Svx8AudioFileTypes.SVX8, baos);
        }

        var decoded = Svx8Codec.read(baos.toByteArray());
        assertEquals(Svx8Sound.CHAN_RIGHT, decoded.chan());
        assertEquals(3, decoded.vhdr().octaves());
        assertEquals(22222, decoded.vhdr().volume());
        assertEquals(5L, decoded.vhdr().repeatHiSamples());
        assertEquals(7L, decoded.vhdr().samplesPerHiCycle());
        assertEquals(VhdrChunk.COMPRESSION_FIBONACCI, decoded.vhdr().compression());
    }

    @Test
    void writer_getAudioFileTypes_reports8svxForSupportedStreams() {
        var writer = new Svx8AudioFileWriter();
        try (var stream = pcmStream(new byte[]{1, 2, 3, 4}, 8000, 1, Map.of())) {
            assertTrue(containsType(writer.getAudioFileTypes(stream), Svx8AudioFileTypes.SVX8));
        } catch (IOException exception) {
            fail(exception);
        }
    }

    @Test
    void audioSystem_getAudioFileFormatAndStream_discoverReader() throws Exception {
        var encoded = loadResource("stereo.8svx");
        var expected = Svx8Codec.read(encoded);

        var fileFormat = AudioSystem.getAudioFileFormat(new ByteArrayInputStream(encoded));
        assertEquals("8SVX", fileFormat.getType().toString());
        assertEquals(2, fileFormat.getFormat().getChannels());
        assertEquals(expected.leftChannel().length, fileFormat.getFrameLength());

        try (var stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(encoded))) {
            assertEquals(AudioFormat.Encoding.PCM_SIGNED, stream.getFormat().getEncoding());
            assertEquals(2, stream.getFormat().getChannels());
            assertEquals(expected.leftChannel().length, stream.getFrameLength());

            var pcm = stream.readNBytes(6);
            assertArrayEquals(
                    new byte[]{
                            expected.leftChannel()[0], expected.rightChannel()[0],
                            expected.leftChannel()[1], expected.rightChannel()[1],
                            expected.leftChannel()[2], expected.rightChannel()[2]
                    },
                    pcm
            );
        }
    }

    @Test
    void audioSystem_write_discoversWriter() throws Exception {
        var baos = new ByteArrayOutputStream();
        var samples = new byte[]{11, 22, 33, 44};

        try (var stream = pcmStream(samples, 8000, 1, Map.of())) {
            var written = AudioSystem.write(stream, Svx8AudioFileTypes.SVX8, baos);
            assertTrue(written > 0);
        }

        var decoded = Svx8Codec.read(baos.toByteArray());
        assertEquals(8000, decoded.vhdr().samplesPerSec());
        assertArrayEquals(samples, decoded.pcmData());
    }

    @Test
    void audioSystem_getAudioFileTypes_includes8svx() throws Exception {
        try (var stream = pcmStream(new byte[]{1, 2, 3, 4}, 8000, 1, Map.of())) {
            assertTrue(containsType(AudioSystem.getAudioFileTypes(stream), Svx8AudioFileTypes.SVX8));
        }
    }

    private static VhdrChunk pcmVhdr(int sampleCount, int sampleRate) {
        return new VhdrChunk(sampleCount, 0, 0, sampleRate, 1, VhdrChunk.COMPRESSION_NONE, 65536);
    }

    private static AudioInputStream pcmStream(byte[] pcm, int sampleRate, int channels, Map<String, Object> properties) {
        var format = new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                sampleRate,
                8,
                channels,
                channels,
                sampleRate,
                false,
                properties
        );
        return new AudioInputStream(new ByteArrayInputStream(pcm), format, pcm.length / channels);
    }

    private static byte[] loadResource(String name) throws IOException {
        try (var stream = Objects.requireNonNull(
                Svx8AudioSystemTest.class.getClassLoader().getResourceAsStream(name),
                () -> "Missing test resource: " + name
        )) {
            return stream.readAllBytes();
        }
    }

    private static boolean containsType(AudioFileFormat.Type[] fileTypes, AudioFileFormat.Type expected) {
        return Arrays.stream(fileTypes).anyMatch(type -> type.toString().equals(expected.toString()));
    }
}

