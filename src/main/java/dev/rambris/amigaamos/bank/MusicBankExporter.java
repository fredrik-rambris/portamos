/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
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
 * Exports a {@link MusicBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code bank.json} — full bank metadata (instruments, songs, patterns).</li>
 *   <li>{@code instrument_NNN.wav} — instrument sample data (or {@code .8svx} when requested).</li>
 * </ul>
 *
 * <h3>JSON schema</h3>
 * <pre>
 * {
 *   "type": "Music",
 *   "bankNumber": 3,
 *   "chipRam": true,
 *   "instruments": [
 *     {
 *       "name": "Not named",
 *       "volume": 45,
 *       "loopStart": 112,     // byte offset from sample start (0 = no loop)
 *       "loopLength": 3247,   // loop length in words (2 = no loop / null sample)
 *       "totalLength": 0,     // reserved word (normally 0)
 *       "sample": "instrument_000.wav"
 *     }
 *   ],
 *   "songs": [
 *     {
 *       "name": "GMC music!",
 *       "tempo": 15,
 *       "sequence": [
 *         [0, 1, 2, 65534],   // 65534 = 0xFFFE = loop; 65535 = 0xFFFF = stop
 *         ...
 *       ]
 *     }
 *   ],
 *   "patterns": [
 *     {
 *       "voices": [
 *         {"period": 254, "duration": 16134},
 *         {"command": "SET_INSTR", "parameter": 0},
 *         ...
 *       ]
 *     }
 *   ]
 * }
 * </pre>
 *
 * @see MusicBankImporter
 */
public class MusicBankExporter {

    /**
     * Amiga standard tuning: period 428 = middle C at this sample rate.
     * Used as the playback frequency for exported instrument samples since
     * the Music bank does not store per-instrument frequencies.
     */
    static final int DEFAULT_SAMPLE_RATE = 8363;

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /** Exports samples as RIFF WAVE (default). */
    public void export(MusicBank bank, Path outDir) throws IOException {
        export(bank, outDir, false);
    }

    /**
     * Exports the music bank to {@code outDir}.
     *
     * @param svx8 if {@code true}, write instrument samples as IFF 8SVX; otherwise RIFF WAVE
     */
    public void export(MusicBank bank, Path outDir, boolean svx8) throws IOException {
        Files.createDirectories(outDir);

        var root = JSON.createObjectNode();
        root.put("type", "Music");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());

        root.set("instruments", exportInstruments(bank, outDir, svx8));
        root.set("songs", exportSongs(bank));
        root.set("patterns", exportPatterns(bank));

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s (%d instruments, %d songs, %d patterns)%n",
                dest, bank.instruments().size(), bank.songs().size(), bank.patterns().size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private ArrayNode exportInstruments(MusicBank bank, Path outDir, boolean svx8)
            throws IOException {
        var arr = JSON.createArrayNode();
        for (int i = 0; i < bank.instruments().size(); i++) {
            var inst = bank.instruments().get(i);
            var ext      = svx8 ? ".8svx" : ".wav";
            var filename = "instrument_%03d%s".formatted(i, ext);

            if (svx8) {
                writeSvx8(inst, outDir.resolve(filename));
            } else {
                writeWav(inst, outDir.resolve(filename));
            }

            var obj = JSON.createObjectNode();
            obj.put("name", inst.name());
            obj.put("volume", inst.volume());
            if (inst.totalLength() != 0) obj.put("totalLength", inst.totalLength());
            if (inst.hasLoop()) {
                obj.put("loopStart", inst.loopOffsetRelative());
                obj.put("loopLength", inst.loopLength());
            }
            obj.put("sample", filename);
            arr.add(obj);
        }
        System.out.printf("Exported %d instrument sample(s)%n", bank.instruments().size());
        return arr;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Audio output
    // ─────────────────────────────────────────────────────────────────────────

    private static void writeWav(MusicBank.Instrument inst, Path dest) throws IOException {
        var unsigned = SampleBankExporter.signedToUnsigned(inst.sampleData());
        var format = new AudioFormat(
                AudioFormat.Encoding.PCM_UNSIGNED,
                DEFAULT_SAMPLE_RATE,
                8, 1, 1,
                DEFAULT_SAMPLE_RATE,
                false);
        var stream = new AudioInputStream(
                new ByteArrayInputStream(unsigned), format, unsigned.length);
        try {
            AudioSystem.write(stream, AudioFileFormat.Type.WAVE, dest.toFile());
        } catch (IllegalArgumentException e) {
            throw new IOException("Cannot write WAV: " + e.getMessage(), e);
        }
    }

    private static void writeSvx8(MusicBank.Instrument inst, Path dest) throws IOException {
        var vhdr = new VhdrChunk(
                inst.sampleData().length,
                0, 0,
                DEFAULT_SAMPLE_RATE,
                1,
                VhdrChunk.COMPRESSION_NONE,
                65536);
        var sound = new Svx8Sound(vhdr, Svx8Sound.CHAN_MONO, inst.sampleData());
        Files.write(dest, Svx8Codec.write(sound));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Songs
    // ─────────────────────────────────────────────────────────────────────────

    private ArrayNode exportSongs(MusicBank bank) {
        var arr = JSON.createArrayNode();
        for (var song : bank.songs()) {
            var obj = JSON.createObjectNode();
            if (!song.name().isEmpty()) obj.put("name", song.name());
            if (song.tempo() != 0) obj.put("tempo", song.tempo());
            var sequence = JSON.createArrayNode();
            for (var seqList : song.sequence()) {
                var vArr = JSON.createArrayNode();
                for (int entry : seqList) vArr.add(entry);
                sequence.add(vArr);
            }
            obj.set("sequence", sequence);
            arr.add(obj);
        }
        return arr;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private ArrayNode exportPatterns(MusicBank bank) {
        var arr = JSON.createArrayNode();
        for (var pattern : bank.patterns()) {
            var obj = JSON.createObjectNode();
            var voices = JSON.createArrayNode();
            for (var noteList : pattern.voices()) {
                var vArr = JSON.createArrayNode();
                for (var item : noteList) vArr.add(exportVoiceItem(item));
                voices.add(vArr);
            }
            obj.set("voices", voices);
            arr.add(obj);
        }
        return arr;
    }

    private ObjectNode exportVoiceItem(MusicBank.VoiceItem item) {
        var obj = JSON.createObjectNode();
        if (item.isCommand()) {
            obj.put("command", item.command().name());
            if (item.parameter() != 0) obj.put("parameter", item.parameter());
        } else {
            obj.put("period", item.period());
            obj.put("duration", item.duration());
        }
        return obj;
    }
}
