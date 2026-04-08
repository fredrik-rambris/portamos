/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.audio;

import dev.rambris.iff.codec.Svx8Options;
import dev.rambris.iff.codec.Svx8Sound;
import dev.rambris.iff.codec.VhdrChunk;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import java.util.LinkedHashMap;
import java.util.Map;

final class Svx8AudioSupport {

    static final String PROP_CHAN                = "svx8.chan";
    static final String PROP_ONE_SHOT_HI_SAMPLES = "svx8.oneShotHiSamples";
    static final String PROP_REPEAT_HI_SAMPLES   = "svx8.repeatHiSamples";
    static final String PROP_SAMPLES_PER_HI_CYCLE = "svx8.samplesPerHiCycle";
    static final String PROP_OCTAVES             = "svx8.octaves";
    static final String PROP_COMPRESSION         = "svx8.compression";
    static final String PROP_VOLUME              = "svx8.volume";

    private Svx8AudioSupport() {
    }

    static AudioFormat toAudioFormat(Svx8Sound sound) {
        var vhdr = sound.vhdr();
        var channels = sound.stereo() ? 2 : 1;

        var properties = new LinkedHashMap<String, Object>();
        properties.put(PROP_CHAN, sound.chan());
        properties.put(PROP_ONE_SHOT_HI_SAMPLES, vhdr.oneShotHiSamples());
        properties.put(PROP_REPEAT_HI_SAMPLES, vhdr.repeatHiSamples());
        properties.put(PROP_SAMPLES_PER_HI_CYCLE, vhdr.samplesPerHiCycle());
        properties.put(PROP_OCTAVES, vhdr.octaves());
        properties.put(PROP_COMPRESSION, vhdr.compression());
        properties.put(PROP_VOLUME, vhdr.volume());

        return new AudioFormat(
                AudioFormat.Encoding.PCM_SIGNED,
                vhdr.samplesPerSec(),
                8,
                channels,
                channels,
                vhdr.samplesPerSec(),
                false,
                Map.copyOf(properties)
        );
    }

    static int frameLength(Svx8Sound sound) {
        return sound.stereo() ? sound.pcmData().length / 2 : sound.pcmData().length;
    }

    static byte[] toInterleavedPcm(Svx8Sound sound) {
        if (!sound.stereo()) {
            return sound.pcmData().clone();
        }
        if ((sound.pcmData().length & 1) != 0) {
            throw new IllegalArgumentException(
                    "Stereo 8SVX BODY data must contain an even number of bytes, got "
                            + sound.pcmData().length);
        }

        var channelLength = sound.pcmData().length / 2;
        var interleaved = new byte[sound.pcmData().length];
        for (var i = 0; i < channelLength; i++) {
            interleaved[i * 2] = sound.pcmData()[i];
            interleaved[i * 2 + 1] = sound.pcmData()[channelLength + i];
        }
        return interleaved;
    }

    static boolean isSupportedPcmFormat(AudioFormat format) {
        if (!AudioFormat.Encoding.PCM_SIGNED.equals(format.getEncoding())) {
            return false;
        }
        if (format.getSampleSizeInBits() != 8) {
            return false;
        }

        var channels = format.getChannels();
        if (channels != 1 && channels != 2) {
            return false;
        }

        var frameSize = format.getFrameSize();
        return frameSize == AudioSystem.NOT_SPECIFIED || frameSize == channels;
    }

    static byte[] toSvx8Body(AudioFormat format, byte[] pcm) {
        var channels = format.getChannels();
        if (channels == 1) {
            return pcm.clone();
        }
        if ((pcm.length & 1) != 0) {
            throw new IllegalArgumentException(
                    "Stereo PCM data must contain an even number of bytes, got " + pcm.length);
        }

        var frames = pcm.length / 2;
        var body = new byte[pcm.length];
        for (var i = 0; i < frames; i++) {
            body[i] = pcm[i * 2];
            body[frames + i] = pcm[i * 2 + 1];
        }
        return body;
    }

    static int frameSize(AudioFormat format) {
        var frameSize = format.getFrameSize();
        return frameSize == AudioSystem.NOT_SPECIFIED ? format.getChannels() : frameSize;
    }

    static int resolveChan(AudioFormat format) {
        if (format.getChannels() == 2) {
            return Svx8Sound.CHAN_STEREO;
        }

        var chan = intProperty(format, PROP_CHAN, Svx8Sound.CHAN_MONO);
        return switch (chan) {
            case Svx8Sound.CHAN_MONO, Svx8Sound.CHAN_LEFT, Svx8Sound.CHAN_RIGHT -> chan;
            default -> Svx8Sound.CHAN_MONO;
        };
    }

    static VhdrChunk resolveVhdr(AudioFormat format, long frameLength) {
        var sampleRate = resolveSampleRate(format);
        var oneShotHiSamples = longProperty(format, PROP_ONE_SHOT_HI_SAMPLES, frameLength);
        var repeatHiSamples = longProperty(format, PROP_REPEAT_HI_SAMPLES, 0);
        var samplesPerHiCycle = longProperty(format, PROP_SAMPLES_PER_HI_CYCLE, 0);
        var octaves = intProperty(format, PROP_OCTAVES, 1);
        var volume = intProperty(format, PROP_VOLUME, 65536);

        return new VhdrChunk(
                oneShotHiSamples,
                repeatHiSamples,
                samplesPerHiCycle,
                sampleRate,
                octaves,
                VhdrChunk.COMPRESSION_NONE,
                volume
        );
    }

    static Svx8Options[] resolveWriteOptions(AudioFormat format) {
        var compression = intProperty(format, PROP_COMPRESSION, VhdrChunk.COMPRESSION_NONE);
        return compression == VhdrChunk.COMPRESSION_FIBONACCI
                ? new Svx8Options[]{Svx8Options.COMPRESSION_FIBONACCI}
                : new Svx8Options[0];
    }

    private static int resolveSampleRate(AudioFormat format) {
        var sampleRate = format.getSampleRate();
        if (sampleRate <= 0) {
            sampleRate = format.getFrameRate();
        }
        if (sampleRate <= 0 || sampleRate > 65535) {
            throw new IllegalArgumentException("8SVX sample rate must be in 1..65535 Hz, got " + sampleRate);
        }
        return Math.round(sampleRate);
    }

    private static long longProperty(AudioFormat format, String key, long defaultValue) {
        var value = format.properties().get(key);
        return value instanceof Number number ? number.longValue() : defaultValue;
    }

    private static int intProperty(AudioFormat format, String key, int defaultValue) {
        var value = format.properties().get(key);
        return value instanceof Number number ? number.intValue() : defaultValue;
    }
}

