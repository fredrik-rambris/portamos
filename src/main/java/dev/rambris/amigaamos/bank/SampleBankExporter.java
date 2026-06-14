/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.SampleBankDto;
import dev.rambris.iff.codec.Svx8Codec;
import dev.rambris.iff.codec.Svx8Sound;
import dev.rambris.iff.codec.VhdrChunk;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link SampleBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code bank.json} — metadata: bank info and per-sample filenames.</li>
 *   <li>{@code sample_NNN.wav} — RIFF WAVE, 8-bit unsigned mono PCM (default).</li>
 *   <li>{@code sample_NNN.8svx} — IFF 8SVX, 8-bit signed mono PCM (with {@code svx8=true}).</li>
 * </ul>
 *
 * <p>AMOS samples are signed 8-bit PCM. WAV 8-bit uses unsigned encoding; the exporter
 * converts automatically (XOR each byte with {@code 0x80}). IFF 8SVX uses signed 8-bit
 * natively so no conversion is needed.
 */
public class SampleBankExporter {


    /** Exports to WAV with index-based filenames (default). */
    public void export(SampleBank bank, Path jsonPath) throws IOException {
        export(bank, jsonPath, false, false);
    }

    /** Exports with index-based filenames. */
    public void export(SampleBank bank, Path jsonPath, boolean svx8) throws IOException {
        export(bank, jsonPath, svx8, false);
    }

    /**
     * Exports the sample bank to {@code jsonPath}.
     *
     * @param bank      the sample bank to export
     * @param jsonPath  destination JSON metadata file; data files are written as siblings
     * @param svx8      if {@code true}, write samples as IFF 8SVX; otherwise RIFF WAVE
     * @param useNames  if {@code true}, derive audio filenames from sample names;
     *                  otherwise use a zero-padded index (e.g. {@code stem-sample000.wav})
     * @throws IOException if any file cannot be written
     */
    public void export(SampleBank bank, Path jsonPath, boolean svx8, boolean useNames)
            throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var sampleDtos = new ArrayList<SampleBankDto.SampleDto>();
        var usedFilenames = new HashSet<String>();

        for (int i = 0; i < bank.samples().size(); i++) {
            var sample = bank.samples().get(i);
            var ext = svx8 ? ".8svx" : ".wav";

            if (sample.isEmpty()) {
                sampleDtos.add(new SampleBankDto.SampleDto(sample.name(), sample.playbackRate(), null));
            } else {
                var filename = useNames
                        ? nameBasedFilename(sample.name(), i, stem, ext, usedFilenames)
                        : stem + "-sample%03d%s".formatted(i, ext);

                if (svx8) {
                    writeSvx8(sample, dir.resolve(filename));
                } else {
                    writeWav(sample, dir.resolve(filename));
                }
                System.out.printf("  sample_%03d: %s, %dHz, %d bytes%n",
                        i, sample.name(), sample.playbackRate(), sample.pcmData().length);

                sampleDtos.add(new SampleBankDto.SampleDto(sample.name(), sample.playbackRate(), filename));
            }
        }

        var dto = new SampleBankDto(SampleBankDto.TYPE, bank.bankNumber() & 0xFFFF, bank.chipRam(), sampleDtos);

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s (%d samples)%n", jsonPath, bank.samples().size());
    }

    /**
     * Derives a filename from the sample name: sanitizes to alphanumeric-plus-underscore,
     * then falls back to index-based naming if the name is blank or would collide.
     */
    private static String nameBasedFilename(String name, int index, String stem, String ext,
                                             Set<String> used) {
        var sanitized = name.trim()
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .replaceAll("_+", "_")
                .replaceAll("^_+|_+$", "");
        if (sanitized.isEmpty()) {
            return stem + "-sample%03d%s".formatted(index, ext);
        }
        var candidate = stem + "-" + sanitized + ext;
        if (used.add(candidate)) {
            return candidate;
        }
        // Collision: append index to disambiguate
        candidate = stem + "-" + sanitized + "-%03d%s".formatted(index, ext);
        used.add(candidate);
        return candidate;
    }

    // -------------------------------------------------------------------------
    // RIFF WAVE output (8-bit unsigned PCM)
    // -------------------------------------------------------------------------

    private static void writeWav(SampleBank.Sample sample, Path dest) throws IOException {
        // WAV 8-bit is unsigned; AMOS PCM is signed — XOR 0x80 to convert
        var unsigned = signedToUnsigned(sample.pcmData());
        var format = new AudioFormat(
                AudioFormat.Encoding.PCM_UNSIGNED,
                sample.playbackRate(),
                8,          // bits per sample
                1,          // mono
                1,          // frame size = 1 byte
                sample.playbackRate(),
                false);     // little-endian (irrelevant for 8-bit)
        var stream = new AudioInputStream(new ByteArrayInputStream(unsigned), format, unsigned.length);
        try {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, dest.toFile());
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot write WAV: " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------
    // IFF 8SVX output (8-bit signed PCM)
    // -------------------------------------------------------------------------

    private static void writeSvx8(SampleBank.Sample sample, Path dest) throws IOException {
        var vhdr = new VhdrChunk(
                sample.pcmData().length,  // oneShotHiSamples = full length (one-shot)
                0,                        // repeatHiSamples  = 0 (no loop)
                0,                        // samplesPerHiCycle
                sample.playbackRate(),
                1,                        // octaves
                VhdrChunk.COMPRESSION_NONE,
                65536                     // volume = max (Amiga Fixed 16.16 = 1.0)
        );
        var sound = new Svx8Sound(vhdr, Svx8Sound.CHAN_MONO, sample.pcmData());
        Files.write(dest, Svx8Codec.write(sound));
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** Converts signed 8-bit PCM to unsigned 8-bit by XOR-ing each byte with 0x80. */
    public static byte[] signedToUnsigned(byte[] signed) {
        var out = new byte[signed.length];
        for (int i = 0; i < signed.length; i++) {
            out[i] = (byte) (signed[i] ^ 0x80);
        }
        return out;
    }

}
