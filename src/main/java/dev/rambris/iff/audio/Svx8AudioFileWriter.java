package dev.rambris.iff.audio;

import dev.rambris.iff.codec.Svx8Codec;
import dev.rambris.iff.codec.Svx8Sound;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.spi.AudioFileWriter;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;

/** Java Sound provider for writing 8-bit signed PCM streams as IFF 8SVX files. */
public final class Svx8AudioFileWriter extends AudioFileWriter {

    private static final AudioFileFormat.Type[] AUDIO_FILE_TYPES = {Svx8AudioFileTypes.SVX8};

    @Override
    public AudioFileFormat.Type[] getAudioFileTypes() {
        return AUDIO_FILE_TYPES.clone();
    }

    @Override
    public boolean isFileTypeSupported(AudioFileFormat.Type fileType) {
        return isSvx8Type(fileType);
    }

    @Override
    public AudioFileFormat.Type[] getAudioFileTypes(AudioInputStream stream) {
        return isFileTypeSupported(Svx8AudioFileTypes.SVX8, stream)
                ? getAudioFileTypes()
                : new AudioFileFormat.Type[0];
    }

    @Override
    public boolean isFileTypeSupported(AudioFileFormat.Type fileType, AudioInputStream stream) {
        return isSvx8Type(fileType) && Svx8AudioSupport.isSupportedPcmFormat(stream.getFormat());
    }

    @Override
    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, OutputStream out)
            throws IOException {
        if (!isFileTypeSupported(fileType, stream)) {
            throw new IllegalArgumentException("Unsupported stream for 8SVX writing: " + stream.getFormat());
        }

        var pcm = stream.readAllBytes();
        var frameSize = Svx8AudioSupport.frameSize(stream.getFormat());
        if (frameSize <= 0 || pcm.length % frameSize != 0) {
            throw new IOException(
                    "PCM byte count " + pcm.length + " is not aligned to frame size " + frameSize);
        }

        var frameLength = pcm.length / frameSize;
        var sound = new Svx8Sound(
                Svx8AudioSupport.resolveVhdr(stream.getFormat(), frameLength),
                Svx8AudioSupport.resolveChan(stream.getFormat()),
                Svx8AudioSupport.toSvx8Body(stream.getFormat(), pcm)
        );
        var encoded = Svx8Codec.write(sound, Svx8AudioSupport.resolveWriteOptions(stream.getFormat()));
        out.write(encoded);
        out.flush();
        return encoded.length;
    }

    @Override
    public int write(AudioInputStream stream, AudioFileFormat.Type fileType, File file)
            throws IOException {
        try (var out = Files.newOutputStream(file.toPath())) {
            return write(stream, fileType, out);
        }
    }

    private static boolean isSvx8Type(AudioFileFormat.Type fileType) {
        return fileType != null
                && (fileType == Svx8AudioFileTypes.SVX8
                || "8SVX".equalsIgnoreCase(fileType.toString())
                || "8svx".equalsIgnoreCase(fileType.getExtension()));
    }
}

