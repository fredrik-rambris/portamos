/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.SampleBankDto;

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
                var freq = s.frequencyHz() != 0 ? s.frequencyHz() : 8363;

                if (s.file() == null) {
                    var name = resolveName(s.name(), null);
                    samples.add(new SampleBank.Sample(name, freq, new byte[0]));
                    continue;
                }

                var name = resolveName(s.name(), s.file());
                var pcm = readAudio(jsonPath.resolveSibling(s.file()));
                samples.add(new SampleBank.Sample(name, freq, pcm));
            }
        }

        return new SampleBank(bankNumber, List.copyOf(samples));
    }

    // -------------------------------------------------------------------------
    // Name resolution
    // -------------------------------------------------------------------------

    /**
     * Returns the sample name to use: the explicit name from the DTO if non-blank,
     * otherwise the stem of the audio filename. Truncates to 8 characters with a warning.
     */
    private static String resolveName(String dtoName, String filename) {
        var name = (dtoName != null && !dtoName.isBlank())
                ? dtoName
                : (filename != null ? AmosBankService.stem(Path.of(filename)) : "");
        if (name.length() > 8) {
            var truncated = name.substring(0, 8);
            System.err.printf("Warning: sample name \"%s\" exceeds 8 characters, truncating to \"%s\"%n",
                    name, truncated);
            name = truncated;
        }
        return name;
    }

    // -------------------------------------------------------------------------
    // Audio reading
    // -------------------------------------------------------------------------

    private static byte[] readAudio(Path path) throws IOException {
        try (var ais = AudioSystem.getAudioInputStream(path.toFile())) {
            var fmt = ais.getFormat();
            if (fmt.getChannels() != 1) {
                throw new IOException("Sample must be mono: " + path
                        + " (got " + fmt.getChannels() + " channels)");
            }
            if (fmt.getSampleSizeInBits() != 8) {
                throw new IOException("Sample must be 8-bit: " + path
                        + " (got " + fmt.getSampleSizeInBits() + "-bit)");
            }
            var pcm = ais.readAllBytes();
            // WAV 8-bit uses unsigned encoding; convert to signed. 8SVX, AIFF, etc. are already signed.
            if (fmt.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
                return SampleBankExporter.signedToUnsigned(pcm);
            }
            return pcm;
        } catch (UnsupportedAudioFileException e) {
            throw new IOException("Unsupported audio file: " + path + " — " + e.getMessage(), e);
        }
    }
}
