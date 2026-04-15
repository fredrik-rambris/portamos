/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

/**
 * Serialises a {@link MusicBank} back to the AMOS {@code AmBk / "Music   "} binary format.
 *
 * <p>The produced binary is byte-for-byte compatible with the format described in
 * {@link MusicBankReader}. Sample data is written as signed 8-bit PCM exactly as stored.
 */
public class MusicBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (!(bank instanceof MusicBank mb)) {
            throw new IllegalArgumentException("Expected MusicBank, got: " + bank.getClass().getSimpleName());
        }
        var payload = buildPayload(mb);
        return AmBkCodec.build(mb.bankNumber(), mb.chipRam(),
                AmosBank.Type.MUSIC.identifier(), payload);
    }

    private byte[] buildPayload(MusicBank mb) throws IOException {
        var instrSection    = buildInstrumentsSection(mb.instruments());
        var songsSection    = buildSongsSection(mb.songs());
        var patternsSection = buildPatternsSection(mb.patterns());

        int instrOff    = 16;
        int songsOff    = instrOff + instrSection.length;
        int patternsOff = songsOff + songsSection.length;

        var baos = new ByteArrayOutputStream(patternsOff + patternsSection.length);
        var out  = new DataOutputStream(baos);
        out.writeInt(instrOff);
        out.writeInt(songsOff);
        out.writeInt(patternsOff);
        out.writeInt(0);  // reserved
        out.write(instrSection);
        out.write(songsSection);
        out.write(patternsSection);
        out.flush();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] buildInstrumentsSection(List<MusicBank.Instrument> instruments)
            throws IOException {
        int count = instruments.size();

        // Null sample is at: 2 (count) + count*32 (records) = nullSampleOffset
        int nullSampleOffset = 2 + count * 32;
        // Sample data starts 4 bytes after null sample
        int sampleDataStart = nullSampleOffset + 4;

        // Compute attack offsets for each instrument (relative to section base)
        var attackOffsets = new int[count];
        int cursor = sampleDataStart;
        for (int i = 0; i < count; i++) {
            attackOffsets[i] = cursor;
            cursor += instruments.get(i).sampleData().length;
        }

        var baos = new ByteArrayOutputStream(cursor);
        var out  = new DataOutputStream(baos);

        // Count word
        out.writeShort(count);

        // Instrument records (32 bytes each)
        for (int i = 0; i < count; i++) {
            var inst = instruments.get(i);
            int attackOff = attackOffsets[i];
            int loopOff;
            int loopLenWords;

            if (inst.hasLoop()) {
                loopOff     = attackOff + inst.loopOffsetRelative();
                loopLenWords = inst.loopLength();
            } else {
                loopOff      = nullSampleOffset;
                loopLenWords = 2;
            }
            int attackLenWords = inst.sampleData().length / 2;

            out.writeInt(attackOff);            // +0 attack offset
            out.writeInt(loopOff);              // +4 loop offset
            out.writeShort(attackLenWords);     // +8 attack length in words
            out.writeShort(loopLenWords);       // +10 loop length in words
            out.writeShort(inst.volume());      // +12 volume
            out.writeShort(inst.totalLength()); // +14 reserved
            out.write(toFixedBytes(inst.name(), 16)); // +16..+31 name
        }

        // Null sample (4 bytes)
        out.writeShort(0);
        out.writeShort(0);

        // Sample data
        for (var inst : instruments) {
            out.write(inst.sampleData());
        }

        out.flush();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Songs
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] buildSongsSection(List<MusicBank.Song> songs) throws IOException {
        int count = songs.size();

        // Build each Music.mus blob first
        var musicBlobs = new byte[count][];
        for (int i = 0; i < count; i++) {
            musicBlobs[i] = buildMusicMus(songs.get(i));
        }

        // Offsets are 1-indexed: offset[s] for s=1..count.
        // offset[1] = 2 + count*4  (= first blob starts after count word + count longs)
        var offsets = new int[count];
        int cursor = 2 + count * 4;
        for (int i = 0; i < count; i++) {
            offsets[i] = cursor;
            cursor += musicBlobs[i].length;
        }


        var baos = new ByteArrayOutputStream(cursor);
        var out  = new DataOutputStream(baos);
        out.writeShort(count);
        for (var off : offsets) out.writeInt(off);
        for (var blob : musicBlobs) out.write(blob);
        out.flush();
        return baos.toByteArray();
    }

    /** Builds one Music.mus blob: 28-byte header + 4 voice pattern-index lists. */
    private byte[] buildMusicMus(MusicBank.Song song) throws IOException {
        int headerSize = 28; // 4 voice-offset words + tempo + free + 16-byte name

        // Voice lists start after the header
        var voiceOffsets = new int[4];
        int cursor = headerSize;
        for (int v = 0; v < 4; v++) {
            voiceOffsets[v] = cursor;
            // Each entry is 2 bytes; +2 for the trailing 0x0000 guard word the AMOS
            // Music editor appends after every terminator (never read at runtime but
            // required for byte-exact round-trip).
            cursor += song.sequence().get(v).size() * 2 + 2;
        }

        var baos = new ByteArrayOutputStream(cursor);
        var out  = new DataOutputStream(baos);

        for (int v = 0; v < 4; v++) out.writeShort(voiceOffsets[v]);
        out.writeShort(song.tempo());  // +8
        out.writeShort(0);             // +10 free
        out.write(toFixedBytes(song.name(), 16)); // +12..+27 name

        for (var voiceList : song.sequence()) {
            for (int entry : voiceList) out.writeShort(entry);
            out.writeShort(0);  // trailing guard word
        }

        out.flush();
        return baos.toByteArray();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private byte[] buildPatternsSection(List<MusicBank.Pattern> patterns) throws IOException {
        int count = patterns.size();
        // 2 bytes count + count*8 bytes offset table, then note lists
        int offsetTableEnd = 2 + count * 8;

        // Compute per-pattern per-voice note-list byte sizes
        var noteOffsets = new int[count][4];
        int cursor = offsetTableEnd;
        for (int i = 0; i < count; i++) {
            for (int v = 0; v < 4; v++) {
                noteOffsets[i][v] = cursor;
                cursor += noteListByteSize(patterns.get(i).voices().get(v));
            }
        }

        var baos = new ByteArrayOutputStream(cursor);
        var out  = new DataOutputStream(baos);

        out.writeShort(count);
        for (int i = 0; i < count; i++) {
            for (int v = 0; v < 4; v++) out.writeShort(noteOffsets[i][v]);
        }
        for (var pattern : patterns) {
            for (var voice : pattern.voices()) {
                writeNoteList(out, voice);
            }
        }

        out.flush();
        return baos.toByteArray();
    }

    /** Computes the byte size of an encoded VoiceItem list (including the implicit END word). */
    private static int noteListByteSize(List<MusicBank.VoiceItem> voice) {
        int size = 2; // END word
        for (var item : voice) {
            if (item.isCommand()) {
                size += 2; // one command word
            } else if (item.duration() != 0) {
                size += 4; // OldNote word + period word
            } else {
                size += 2; // bare period word
            }
        }
        return size;
    }

    /** Writes an encoded VoiceItem list followed by the implicit END word (0x8000). */
    private static void writeNoteList(DataOutputStream out, List<MusicBank.VoiceItem> voice)
            throws IOException {
        for (var item : voice) {
            if (item.isCommand()) {
                out.writeShort(0x8000 | (item.command().label() << 8) | item.parameter());
            } else if (item.duration() != 0) {
                out.writeShort(0x4000 | item.duration()); // OldNote word
                out.writeShort(item.period());
            } else {
                out.writeShort(item.period()); // bare period (inherited duration)
            }
        }
        out.writeShort(0x8000); // END
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    /** Fixed-length byte array; space-padded, truncated if too long. */
    private static byte[] toFixedBytes(String s, int len) {
        var result = new byte[len];
        Arrays.fill(result, (byte) ' ');
        var src = s.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(src, 0, result, 0, Math.min(src.length, len));
        return result;
    }
}
