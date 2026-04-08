package dev.rambris.iff.audio;

import dev.rambris.iff.codec.Svx8Codec;
import dev.rambris.iff.exceptions.IffException;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.UnsupportedAudioFileException;
import javax.sound.sampled.spi.AudioFileReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

/** Java Sound provider for reading IFF 8SVX files as 8-bit signed PCM streams. */
public final class Svx8AudioFileReader extends AudioFileReader {

    @Override
    public Svx8AudioFileFormat getAudioFileFormat(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        return decode(stream.readAllBytes()).fileFormat();
    }

    @Override
    public Svx8AudioFileFormat getAudioFileFormat(URL url)
            throws UnsupportedAudioFileException, IOException {
        try (var stream = url.openStream()) {
            return getAudioFileFormat(stream);
        }
    }

    @Override
    public Svx8AudioFileFormat getAudioFileFormat(File file)
            throws UnsupportedAudioFileException, IOException {
        try (var stream = java.nio.file.Files.newInputStream(file.toPath())) {
            return getAudioFileFormat(stream);
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(InputStream stream)
            throws UnsupportedAudioFileException, IOException {
        var decoded = decode(stream.readAllBytes());
        return new AudioInputStream(
                new ByteArrayInputStream(decoded.pcm()),
                decoded.fileFormat().getFormat(),
                decoded.fileFormat().getFrameLength()
        );
    }

    @Override
    public AudioInputStream getAudioInputStream(URL url)
            throws UnsupportedAudioFileException, IOException {
        try (var stream = url.openStream()) {
            return getAudioInputStream(stream);
        }
    }

    @Override
    public AudioInputStream getAudioInputStream(File file)
            throws UnsupportedAudioFileException, IOException {
        try (var stream = java.nio.file.Files.newInputStream(file.toPath())) {
            return getAudioInputStream(stream);
        }
    }

    private static DecodedSvx8 decode(byte[] data) throws UnsupportedAudioFileException {
        try {
            var sound = Svx8Codec.read(data);
            var audioFormat = Svx8AudioSupport.toAudioFormat(sound);
            var frameLength = Svx8AudioSupport.frameLength(sound);
            var fileFormat = new Svx8AudioFileFormat(audioFormat, frameLength, audioFormat.properties());
            var pcm = Svx8AudioSupport.toInterleavedPcm(sound);
            return new DecodedSvx8(fileFormat, pcm);
        } catch (IffException | IllegalArgumentException exception) {
            var unsupported = new UnsupportedAudioFileException(exception.getMessage());
            unsupported.initCause(exception);
            throw unsupported;
        }
    }

    private record DecodedSvx8(Svx8AudioFileFormat fileFormat, byte[] pcm) {
    }
}


