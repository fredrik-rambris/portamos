/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an AMOS Professional Music bank ({@code AmBk / "Music   "}).
 *
 * <h3>Payload layout (offset 0 = start of payload, all big-endian)</h3>
 * <pre>
 *   +0  instruments_offset  (long) – byte offset from payload[0] to Instruments section
 *   +4  songs_offset        (long) – byte offset from payload[0] to Songs section
 *   +8  patterns_offset     (long) – byte offset from payload[0] to Patterns section
 *   +12 reserved            (long) – always 0
 * </pre>
 *
 * <h3>Instruments section</h3>
 * <pre>
 *   [2] count
 *   [count × 32] instrument records (see {@link MusicBank.Instrument})
 *   [4] null sample (0x0000 0x0000)
 *   [sample data ...]
 * </pre>
 *
 * <h3>Songs section</h3>
 * <pre>
 *   [2] count  (songs are 1-indexed)
 *   [count × 4] long offsets from Songs base to each Music.mus header
 *   [Music.mus headers + voice pattern lists ...]
 * </pre>
 *
 * <h3>Patterns section</h3>
 * <pre>
 *   [2] count
 *   [count × 8] word offsets: 4 per pattern (voice 0-3), from Patterns base
 *   [note lists ...]
 * </pre>
 */
public class MusicBankReader {

    /** Null sample offset value (2 bytes count header + N*32 instrument records). */
    private static final int NULL_SAMPLE_LOOP_LENGTH = 2;

    public static MusicBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static MusicBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        return parse(hdr.bankNumber(), hdr.chipRam(), hdr.payload());
    }

    private static MusicBank parse(short bankNumber, boolean chipRam, byte[] payload)
            throws IOException {
        if (payload.length < 16) {
            throw new IOException("Music bank payload too small (" + payload.length + " bytes)");
        }

        var buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int instrOff    = buf.getInt();  // +0
        int songsOff    = buf.getInt();  // +4
        int patternsOff = buf.getInt();  // +8
        buf.getInt();                    // +12 reserved

        var instruments = parseInstruments(payload, instrOff);
        var songs       = parseSongs(payload, songsOff);
        var patterns    = parsePatterns(payload, patternsOff);

        return new MusicBank(bankNumber, chipRam,
                List.copyOf(instruments),
                List.copyOf(songs),
                List.copyOf(patterns));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Instruments
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Instrument> parseInstruments(byte[] p, int base)
            throws IOException {
        if (base + 2 > p.length) throw new IOException("Instruments section out of range");
        int count = ((p[base] & 0xFF) << 8) | (p[base + 1] & 0xFF);

        // Null sample offset = base + 2 (count word) + count*32 (records)
        int nullSampleOffset = base + 2 + count * 32;

        var result = new ArrayList<MusicBank.Instrument>(count);
        for (int i = 0; i < count; i++) {
            int recBase = base + 2 + i * 32;
            if (recBase + 32 > p.length) {
                throw new IOException("Instrument record " + i + " out of range");
            }

            int attackOffset = readInt(p, recBase);
            int loopOffset   = readInt(p, recBase + 4);
            int attackLength = readUShort(p, recBase + 8);
            int loopLength   = readUShort(p, recBase + 10);
            int volume       = readUShort(p, recBase + 12);
            int totalLength  = readUShort(p, recBase + 14);
            var name      = readName(p, recBase + 16, 16);

            // Extract sample data from payload (attack_length words = attack_length*2 bytes)
            int sampleStart = base + attackOffset;
            int sampleBytes = attackLength * 2;
            if (sampleStart + sampleBytes > p.length) {
                throw new IOException("Instrument " + i + " sample data out of range");
            }
            var sampleData = new byte[sampleBytes];
            System.arraycopy(p, sampleStart, sampleData, 0, sampleBytes);

            // Compute loop start relative to sample start
            int loopOffsetRelative;
            if (loopOffset == nullSampleOffset || loopLength == NULL_SAMPLE_LOOP_LENGTH) {
                loopOffsetRelative = 0; // no loop
            } else {
                loopOffsetRelative = (base + loopOffset) - sampleStart;
            }

            result.add(new MusicBank.Instrument(name, volume, totalLength,
                    loopOffsetRelative, loopLength, sampleData));
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Songs
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Song> parseSongs(byte[] p, int base) throws IOException {
        if (base + 2 > p.length) throw new IOException("Songs section out of range");
        int count = readUShort(p, base);

        var result = new ArrayList<MusicBank.Song>(count);
        for (int s = 1; s <= count; s++) {
            // offset[s] stored as a long at Songs + s*4 - 2 (1-indexed)
            int offAddr = base + s * 4 - 2;
            if (offAddr + 4 > p.length) throw new IOException("Song " + s + " offset out of range");
            int musicMusOff = readInt(p, offAddr);
            int musicMusBase = base + musicMusOff;

            if (musicMusBase + 28 > p.length) {
                throw new IOException("Music.mus " + s + " header out of range");
            }

            // Read 4 voice offsets (relative to musicMusBase) and tempo
            int v0Off   = readUShort(p, musicMusBase);
            int v1Off   = readUShort(p, musicMusBase + 2);
            int v2Off   = readUShort(p, musicMusBase + 4);
            int v3Off   = readUShort(p, musicMusBase + 6);
            int tempo   = readUShort(p, musicMusBase + 8);
            // word at +10 = free, skip
            var name = readName(p, musicMusBase + 12, 16);

            int[] voiceOffsets = { v0Off, v1Off, v2Off, v3Off };
            var sequence = new ArrayList<List<Integer>>(4);
            for (int v = 0; v < 4; v++) {
                sequence.add(readPatternList(p, musicMusBase + voiceOffsets[v]));
            }

            result.add(new MusicBank.Song(name, tempo, List.copyOf(sequence)));
        }
        return result;
    }

    /** Reads a pattern index list from the given absolute payload offset.
     *  The list is terminated by a negative value (0xFFFE = loop, 0xFFFF = stop). */
    private static List<Integer> readPatternList(byte[] p, int pos) throws IOException {
        var list = new ArrayList<Integer>();
        while (pos + 2 <= p.length) {
            int w = readUShort(p, pos);
            pos += 2;
            list.add(w);
            if ((w & 0x8000) != 0) break; // negative terminator
        }
        return List.copyOf(list);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Patterns
    // ─────────────────────────────────────────────────────────────────────────

    private static List<MusicBank.Pattern> parsePatterns(byte[] p, int base) throws IOException {
        if (base + 2 > p.length) throw new IOException("Patterns section out of range");
        int count = readUShort(p, base);

        var result = new ArrayList<MusicBank.Pattern>(count);
        for (int i = 0; i < count; i++) {
            var voices = new ArrayList<List<MusicBank.VoiceItem>>(4);
            for (int v = 0; v < 4; v++) {
                int offAddr = base + 2 + i * 8 + v * 2;
                if (offAddr + 2 > p.length) {
                    throw new IOException("Pattern " + i + " voice " + v + " offset out of range");
                }
                int noteOff = readUShort(p, offAddr);
                int notePos = base + noteOff;
                voices.add(readNoteList(p, notePos));
            }
            result.add(new MusicBank.Pattern(List.copyOf(voices)));
        }
        return result;
    }

    /** Reads a note/command list, terminated by label 0 (0x8000). */
    private static List<MusicBank.VoiceItem> readNoteList(byte[] p, int pos) throws IOException {
        var list = new ArrayList<MusicBank.VoiceItem>();
        while (pos + 2 <= p.length) {
            int w = readUShort(p, pos);
            pos += 2;
            if (w == 0x8000) break; // END_PATTERN — not stored, implicit
            if ((w & 0x8000) != 0) {
                // Command word: bit15=1, bits 14-8 = label, bits 7-0 = parameter
                int label = (w >> 8) & 0x7F;
                int param = w & 0xFF;
                var command = MusicBank.Command.fromLabel(label);
                if (command == null) {
                    throw new IOException("Unknown music command label 0x%02X at word 0x%04X"
                            .formatted(label, w));
                }
                list.add(new MusicBank.VoiceItem(0, 0, command, param));
            } else if ((w & 0x4000) != 0) {
                // OldNote: bit14=1; bits 13-0 encode per-note volume + tick duration.
                // Next word is the period (0 = silence).
                int duration = w & 0x3FFF;
                int period = (pos + 2 <= p.length) ? readUShort(p, pos) : 0;
                pos += 2;
                list.add(new MusicBank.VoiceItem(period, duration, null, 0));
            } else {
                // Bare period word (inherited duration — new-format note)
                list.add(new MusicBank.VoiceItem(w, 0, null, 0));
            }
        }
        return List.copyOf(list);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private static int readInt(byte[] p, int off) {
        return ((p[off] & 0xFF) << 24)
             | ((p[off+1] & 0xFF) << 16)
             | ((p[off+2] & 0xFF) << 8)
             |  (p[off+3] & 0xFF);
    }

    private static int readUShort(byte[] p, int off) {
        return ((p[off] & 0xFF) << 8) | (p[off+1] & 0xFF);
    }

    private static String readName(byte[] p, int off, int len) {
        // Trim trailing spaces (but preserve internal spaces)
        int end = off + len;
        while (end > off && (p[end-1] == ' ' || p[end-1] == 0)) end--;
        return new String(p, off, end - off, StandardCharsets.ISO_8859_1);
    }
}
