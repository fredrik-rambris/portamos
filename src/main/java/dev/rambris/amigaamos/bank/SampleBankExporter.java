/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

/**
 * Exports a {@link SampleBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code bank.json} — metadata: bank info and per-sample index.</li>
 *   <li>{@code sample_NNN.wav} — RIFF WAVE, 8-bit unsigned mono PCM (default).</li>
 *   <li>{@code sample_NNN.8svx} — IFF 8SVX, 8-bit signed mono PCM (with {@code svx8=true}).</li>
 * </ul>
 *
 * <p>AMOS samples are signed 8-bit PCM. WAV 8-bit uses unsigned encoding; the exporter
 * converts automatically (XOR each byte with {@code 0x80}). IFF 8SVX uses signed 8-bit
 * natively so no conversion is needed.
 */
public class SampleBankExporter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Exports to WAV (default). */
    public void export(SampleBank bank, Path outDir) throws IOException {
        export(bank, outDir, false);
    }

    /**
     * Exports the sample bank to {@code outDir}.
     *
     * @param bank   the sample bank to export
     * @param outDir target directory
     * @param svx8   if {@code true}, write samples as IFF 8SVX; otherwise RIFF WAVE
     * @throws IOException if any file cannot be written
     */
    public void export(SampleBank bank, Path outDir, boolean svx8) throws IOException {
        Files.createDirectories(outDir);

        var root = JSON.createObjectNode();
        root.put("type", "Samples");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());

        var samplesNode = root.putArray("samples");

        for (int i = 0; i < bank.samples().size(); i++) {
            var sample = bank.samples().get(i);
            var sn = samplesNode.addObject();
            sn.put("index", i);
            sn.put("name", sample.name());
            sn.put("frequencyHz", sample.frequencyHz());

            if (sample.isEmpty()) {
                sn.put("empty", true);
            } else {
                var filename = svx8
                        ? "sample_%03d.8svx".formatted(i)
                        : "sample_%03d.wav".formatted(i);
                sn.put("file", filename);

                if (svx8) {
                    writeSvx8(sample, outDir.resolve(filename));
                } else {
                    writeWav(sample, outDir.resolve(filename));
                }
                System.out.printf("  sample_%03d: %s, %dHz, %d bytes%n",
                        i, sample.name(), sample.frequencyHz(), sample.pcmData().length);
            }
        }

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s (%d samples)%n", dest, bank.samples().size());
    }

    // -------------------------------------------------------------------------
    // RIFF WAVE output (8-bit unsigned PCM)
    // -------------------------------------------------------------------------

    private static void writeWav(SampleBank.Sample sample, Path dest) throws IOException {
        // WAV 8-bit is unsigned; AMOS PCM is signed — XOR 0x80 to convert
        var unsigned = signedToUnsigned(sample.pcmData());
        var format = new AudioFormat(
                AudioFormat.Encoding.PCM_UNSIGNED,
                sample.frequencyHz(),
                8,          // bits per sample
                1,          // mono
                1,          // frame size = 1 byte
                sample.frequencyHz(),
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
                sample.frequencyHz(),
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
    static byte[] signedToUnsigned(byte[] signed) {
        var out = new byte[signed.length];
        for (int i = 0; i < signed.length; i++) {
            out[i] = (byte) (signed[i] ^ 0x80);
        }
        return out;
    }
}
