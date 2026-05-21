/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.MusicBankDto;
import dev.rambris.iff.codec.Svx8Codec;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link MusicBank} from a JSON file previously produced by {@link MusicBankExporter}.
 *
 * <p>Sample data is loaded from the WAV, 8SVX, or raw files referenced by each instrument's
 * {@code "sample"} field. All paths are resolved relative to the directory containing
 * {@code bank.json}.
 */
public class MusicBankImporter {


    public MusicBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), MusicBankDto.class);
        var dir = jsonPath.getParent();

        short bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 1);
        boolean chipRam = dto.chipRam() == null || dto.chipRam();

        var instruments = readInstruments(dto.instruments(), dir);
        var songs = readSongs(dto.songs());
        var patterns = readPatterns(dto.patterns());

        return new MusicBank(bankNumber, chipRam,
                List.copyOf(instruments),
                List.copyOf(songs),
                List.copyOf(patterns));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Instrument> readInstruments(
            List<MusicBankDto.InstrumentDto> dtos, Path dir) throws IOException {
        var result = new ArrayList<MusicBank.Instrument>();
        if (dtos == null) return result;
        for (var d : dtos) {
            var name = d.name() != null ? d.name() : "";
            var volume = d.volume();
            var totalLength = d.totalLength() != null ? d.totalLength() : 0;
            var loopStart = d.loopStart() != null ? d.loopStart() : 0;
            var loopLength = d.loopLength() != null ? d.loopLength() : 2;
            var sampleFile = d.sample();

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

    private static List<MusicBank.Song> readSongs(List<MusicBankDto.SongDto> dtos) {
        var result = new ArrayList<MusicBank.Song>();
        if (dtos == null) return result;
        for (var d : dtos) {
            var name = d.name() != null ? d.name() : "";
            var tempo = d.tempo() != null ? d.tempo() : 0;
            var sequence = new ArrayList<List<Integer>>(4);
            for (int v = 0; v < 4; v++) {
                var list = new ArrayList<Integer>();
                if (d.sequence() != null && v < d.sequence().size()) {
                    list.addAll(d.sequence().get(v));
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

    private static List<MusicBank.Pattern> readPatterns(List<MusicBankDto.PatternDto> dtos) {
        var result = new ArrayList<MusicBank.Pattern>();
        if (dtos == null) return result;
        for (var d : dtos) {
            var voices = new ArrayList<List<MusicBank.VoiceItem>>(4);
            for (int v = 0; v < 4; v++) {
                var list = new ArrayList<MusicBank.VoiceItem>();
                if (d.voices() != null && v < d.voices().size()) {
                    for (var item : d.voices().get(v)) {
                        list.add(readVoiceItem(item));
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

    private static MusicBank.VoiceItem readVoiceItem(MusicBankDto.VoiceItemDto d) {
        if (d.command() != null) {
            var command = MusicBank.Command.valueOf(d.command());
            var parameter = d.parameter() != null ? d.parameter() : 0;
            return new MusicBank.VoiceItem(0, 0, command, parameter);
        } else {
            var period = d.period() != null ? d.period() : 0;
            var duration = d.duration() != null ? d.duration() : 0;
            return new MusicBank.VoiceItem(period, duration, null, 0);
        }
    }
}
