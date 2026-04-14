/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rambris.iff.codec.Svx8Codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    private static final ObjectMapper JSON = new ObjectMapper();

    public SampleBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var bankNumber = (short) root.path("bankNumber").asInt(0);

        var samples = new ArrayList<SampleBank.Sample>();
        for (var sn : root.path("samples")) {
            var name = sn.path("name").asText("");
            var freq = sn.path("frequencyHz").asInt(8363);

            if (sn.path("empty").asBoolean(false) || !sn.has("file")) {
                samples.add(new SampleBank.Sample(name, freq, new byte[0]));
                continue;
            }

            var file = sn.path("file").asText();
            var audioPath = jsonPath.resolveSibling(file);
            var pcm = readAudio(audioPath, freq);
            samples.add(new SampleBank.Sample(name, freq, pcm));
        }

        return new SampleBank(bankNumber, List.copyOf(samples));
    }

    // -------------------------------------------------------------------------
    // Audio reading
    // -------------------------------------------------------------------------

    /**
     * Reads signed 8-bit PCM from a WAV or 8SVX file.
     *
     * @param path audio file path
     * @param hintFreq frequency hint (used only when the file format omits it — not needed for WAV/8SVX)
     * @return signed 8-bit PCM samples
     */
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
