/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.MusicBankDto;
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
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link MusicBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code bank.json} — full bank metadata (instruments, songs, patterns).</li>
 *   <li>{@code instrument_NNN.wav} — instrument sample data (or {@code .8svx} when requested).</li>
 * </ul>
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


    /** Exports samples as RIFF WAVE (default). */
    public void export(MusicBank bank, Path jsonPath) throws IOException {
        export(bank, jsonPath, false);
    }

    /**
     * Exports the music bank to {@code jsonPath}.
     *
     * @param jsonPath destination JSON metadata file; data files are written as siblings
     * @param svx8     if {@code true}, write instrument samples as IFF 8SVX; otherwise RIFF WAVE
     */
    public void export(MusicBank bank, Path jsonPath, boolean svx8) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var dto = new MusicBankDto(
                MusicBankDto.TYPE,
                bank.bankNumber() & 0xFFFF,
                bank.chipRam(),
                buildInstrumentDtos(bank, dir, stem, svx8),
                buildSongDtos(bank),
                buildPatternDtos(bank));

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s (%d instruments, %d songs, %d patterns)%n",
                jsonPath, bank.instruments().size(), bank.songs().size(), bank.patterns().size());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private List<MusicBankDto.InstrumentDto> buildInstrumentDtos(
            MusicBank bank, Path dir, String stem, boolean svx8) throws IOException {
        var result = new ArrayList<MusicBankDto.InstrumentDto>();
        for (int i = 0; i < bank.instruments().size(); i++) {
            var inst = bank.instruments().get(i);
            var ext      = svx8 ? ".8svx" : ".wav";
            var filename = stem + "-instrument%03d%s".formatted(i, ext);

            if (svx8) {
                writeSvx8(inst, dir.resolve(filename));
            } else {
                writeWav(inst, dir.resolve(filename));
            }

            result.add(new MusicBankDto.InstrumentDto(
                    inst.name(),
                    inst.volume(),
                    inst.totalLength() != 0 ? inst.totalLength() : null,
                    inst.hasLoop() ? inst.loopOffsetRelative() : null,
                    inst.hasLoop() ? inst.loopLength() : null,
                    filename));
        }
        System.out.printf("Exported %d instrument sample(s)%n", bank.instruments().size());
        return result;
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

    private List<MusicBankDto.SongDto> buildSongDtos(MusicBank bank) {
        var result = new ArrayList<MusicBankDto.SongDto>();
        for (var song : bank.songs()) {
            var sequence = song.sequence().stream()
                    .map(List::copyOf)
                    .toList();
            result.add(new MusicBankDto.SongDto(
                    song.name().isEmpty() ? null : song.name(),
                    song.tempo() != 0 ? song.tempo() : null,
                    sequence));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private List<MusicBankDto.PatternDto> buildPatternDtos(MusicBank bank) {
        var result = new ArrayList<MusicBankDto.PatternDto>();
        for (var pattern : bank.patterns()) {
            var voices = pattern.voices().stream()
                    .map(noteList -> noteList.stream()
                            .map(this::buildVoiceItemDto)
                            .toList())
                    .toList();
            result.add(new MusicBankDto.PatternDto(voices));
        }
        return result;
    }

    private MusicBankDto.VoiceItemDto buildVoiceItemDto(MusicBank.VoiceItem item) {
        if (item.isCommand()) {
            return new MusicBankDto.VoiceItemDto(
                    item.command().name(),
                    item.parameter() != 0 ? item.parameter() : null,
                    null, null);
        } else {
            return new MusicBankDto.VoiceItemDto(null, null, item.period(), item.duration());
        }
    }
}
