/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.SampleBankDto;
import dev.rambris.iff.codec.Svx8Codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link SampleBank} from a JSON metadata file previously produced by
 * {@link SampleBankExporter}.
 *
 * <p>Audio files referenced in the JSON are resolved relative to the JSON file.
 * Both RIFF WAVE ({@code .wav}) and IFF 8SVX ({@code .8svx}) files are accepted;
 * the format is detected from the file extension. WAV files must be 8-bit mono
 * (unsigned PCM); 8SVX files must be mono.
 */
public class SampleBankImporter {


    public SampleBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), SampleBankDto.class);

        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 0);

        var samples = new ArrayList<SampleBank.Sample>();
        if (dto.samples() != null) {
            for (var s : dto.samples()) {
                var name = s.name() != null ? s.name() : "";
                var freq = s.frequencyHz() != 0 ? s.frequencyHz() : 8363;

                if (Boolean.TRUE.equals(s.empty()) || s.file() == null) {
                    samples.add(new SampleBank.Sample(name, freq, new byte[0]));
                    continue;
                }

                var pcm = readAudio(jsonPath.resolveSibling(s.file()), freq);
                samples.add(new SampleBank.Sample(name, freq, pcm));
            }
        }

        return new SampleBank(bankNumber, List.copyOf(samples));
    }

    // -------------------------------------------------------------------------
    // Audio reading
    // -------------------------------------------------------------------------

    private static byte[] readAudio(Path path, int hintFreq) throws IOException {
        var name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".8svx") || name.endsWith(".svx")) {
            return readSvx8(path);
        }
        return readWav(path);
    }

    private static byte[] readWav(Path path) throws IOException {
        try (var ais = AudioSystem.getAudioInputStream(path.toFile())) {
            var fmt = ais.getFormat();
            validateMono(fmt, path);
            if (fmt.getSampleSizeInBits() != 8) {
                throw new IOException("WAV must be 8-bit mono: " + path
                        + " (got " + fmt.getSampleSizeInBits() + "-bit)");
            }
            var unsigned = ais.readAllBytes();
            if (fmt.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
                return SampleBankExporter.signedToUnsigned(unsigned); // same XOR converts back
            }
            // PCM_SIGNED 8-bit — already correct, though unusual for WAV
            return unsigned;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file: " + path + " — " + e.getMessage(), e);
        }
    }

    private static byte[] readSvx8(Path path) throws IOException {
        var sound = Svx8Codec.read(path);
        if (sound.stereo()) {
            throw new IOException("8SVX must be mono: " + path);
        }
        return sound.pcmData();
    }

    private static void validateMono(AudioFormat fmt, Path path) throws IOException {
        if (fmt.getChannels() != 1) {
            throw new IOException("Audio must be mono: " + path
                    + " (got " + fmt.getChannels() + " channels)");
        }
    }
}
