/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rambris.iff.codec.Svx8Codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports a {@link MusicBank} from a JSON file previously produced by {@link MusicBankExporter}.
 *
 * <p>Sample data is loaded from the WAV, 8SVX, or raw files referenced by each instrument's
 * {@code "sample"} field. All paths are resolved relative to the directory containing
 * {@code bank.json}.
 */
public class MusicBankImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    public MusicBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());
        var dir  = jsonPath.getParent();

        short bankNumber = (short) root.path("bankNumber").asInt(1);
        boolean chipRam  = root.path("chipRam").asBoolean(true);

        var instruments = readInstruments(root.path("instruments"), dir);
        var songs       = readSongs(root.path("songs"));
        var patterns    = readPatterns(root.path("patterns"));

        return new MusicBank(bankNumber, chipRam,
                List.copyOf(instruments),
                List.copyOf(songs),
                List.copyOf(patterns));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Instrument> readInstruments(JsonNode arr, Path dir)
            throws IOException {
        var result = new ArrayList<MusicBank.Instrument>();
        if (arr == null || arr.isMissingNode()) return result;
        for (var n : arr) {
            var name        = n.path("name").asText("");
            var volume      = n.path("volume").asInt(64);
            var totalLength = n.path("totalLength").asInt(0);
            var loopStart   = n.path("loopStart").asInt(0);
            var loopLength  = n.path("loopLength").asInt(2);
            var sampleFile  = n.path("sample").asText(null);

            byte[] sampleData;
            if (sampleFile != null && !sampleFile.isEmpty()) {
                var samplePath = dir.resolve(sampleFile);
                if (!Files.exists(samplePath)) {
                    throw new IOException("Sample file not found: " + samplePath);
                }
                sampleData = readSampleFile(samplePath);
            } else {
                sampleData = new byte[0];
            }

            result.add(new MusicBank.Instrument(name, volume, totalLength,
                    loopStart, loopLength, sampleData));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Songs
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Song> readSongs(JsonNode arr) {
        var result = new ArrayList<MusicBank.Song>();
        if (arr == null || arr.isMissingNode()) return result;
        for (var n : arr) {
            var name     = n.path("name").asText("");
            var tempo    = n.path("tempo").asInt(0);
            var sequence = new ArrayList<List<Integer>>(4);
            var seqNode  = n.path("sequence");
            for (int v = 0; v < 4; v++) {
                var list = new ArrayList<Integer>();
                if (!seqNode.isMissingNode() && v < seqNode.size()) {
                    for (var entry : seqNode.get(v)) {
                        list.add(entry.asInt());
                    }
                }
                sequence.add(List.copyOf(list));
            }
            result.add(new MusicBank.Song(name, tempo, List.copyOf(sequence)));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Pattern> readPatterns(JsonNode arr) {
        var result = new ArrayList<MusicBank.Pattern>();
        if (arr == null || arr.isMissingNode()) return result;
        for (var n : arr) {
            var voices     = new ArrayList<List<MusicBank.VoiceItem>>(4);
            var voicesNode = n.path("voices");
            for (int v = 0; v < 4; v++) {
                var list = new ArrayList<MusicBank.VoiceItem>();
                if (!voicesNode.isMissingNode() && v < voicesNode.size()) {
                    for (var w : voicesNode.get(v)) {
                        list.add(readVoiceItem(w));
                    }
                }
                voices.add(List.copyOf(list));
            }
            result.add(new MusicBank.Pattern(List.copyOf(voices)));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio reading
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Reads signed 8-bit PCM from a sample file.
     * Accepts {@code .wav} (8-bit unsigned), {@code .8svx} / {@code .svx} (IFF 8SVX),
     * or {@code .raw} (raw signed 8-bit).
     */
    private static byte[] readSampleFile(Path path) throws IOException {
        var name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".8svx") || name.endsWith(".svx")) {
            var sound = Svx8Codec.read(path);
            if (sound.stereo()) throw new IOException("8SVX must be mono: " + path);
            return sound.pcmData();
        }
        if (name.endsWith(".wav")) {
            try (var ais = AudioSystem.getAudioInputStream(path.toFile())) {
                var fmt = ais.getFormat();
                if (fmt.getChannels() != 1) {
                    throw new IOException("WAV must be mono: " + path);
                }
                if (fmt.getSampleSizeInBits() != 8) {
                    throw new IOException("WAV must be 8-bit: " + path);
                }
                var data = ais.readAllBytes();
                if (fmt.getEncoding() == AudioFormat.Encoding.PCM_UNSIGNED) {
                    return SampleBankExporter.signedToUnsigned(data); // XOR 0x80 converts both ways
                }
                return data;
            } catch (UnsupportedAudioFileException e) {
                throw new IOException("Unsupported audio file: " + path + " — " + e.getMessage(), e);
            }
        }
        // Fallback: raw signed 8-bit PCM
        return Files.readAllBytes(path);
    }

    private static MusicBank.VoiceItem readVoiceItem(JsonNode n) {
        var cmdNode = n.path("command");
        if (!cmdNode.isMissingNode()) {
            var command   = MusicBank.Command.valueOf(cmdNode.asText());
            var parameter = n.path("parameter").asInt(0);
            return new MusicBank.VoiceItem(0, 0, command, parameter);
        } else {
            var period   = n.path("period").asInt(0);
            var duration = n.path("duration").asInt(0);
            return new MusicBank.VoiceItem(period, duration, null, 0);
        }
    }
}
