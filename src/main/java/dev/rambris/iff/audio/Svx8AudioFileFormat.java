package dev.rambris.iff.audio;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import java.util.Map;

/** AudioFileFormat carrying 8SVX-specific metadata properties. */
public final class Svx8AudioFileFormat extends AudioFileFormat {

    Svx8AudioFileFormat(AudioFormat format, int frameLength, Map<String, Object> properties) {
        super(Svx8AudioFileTypes.SVX8, format, frameLength, properties);
    }
}


