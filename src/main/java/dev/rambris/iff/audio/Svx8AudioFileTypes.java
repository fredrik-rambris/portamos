package dev.rambris.iff.audio;

import javax.sound.sampled.AudioFileFormat;

/** Shared Java Sound file-type constants for IFF 8SVX audio. */
public final class Svx8AudioFileTypes {

    /** Java Sound file type for IFF 8SVX audio files. */
    public static final AudioFileFormat.Type SVX8 = new AudioFileFormat.Type("8SVX", "8svx") { };

    private Svx8AudioFileTypes() {
    }
}

