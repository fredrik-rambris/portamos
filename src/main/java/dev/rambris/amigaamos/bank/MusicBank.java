/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * In-memory model of an AMOS Professional Music bank ({@code AmBk / "Music   "}).
 *
 * <p>The bank is structured in three independent sections:
 * <ol>
 *   <li><b>Instruments</b> – sample data with attack/loop regions</li>
 *   <li><b>Songs</b> – sequences of pattern numbers for each of the 4 voices</li>
 *   <li><b>Patterns</b> – per-voice note/command lists</li>
 * </ol>
 *
 * <p>Binary format: see {@link MusicBankReader}.
 */
public record MusicBank(
        short bankNumber,
        boolean chipRam,
        List<Instrument> instruments,
        List<Song> songs,
        List<Pattern> patterns
) implements AmosBank {

    @Override
    public AmosBank.Type type() { return AmosBank.Type.MUSIC; }

    @Override
    public BankWriter writer() { return new MusicBankWriter(); }

    // ─────────────────────────────────────────────────────────────────────────
    // Instrument
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One instrument entry (32 bytes of metadata plus sample data).
     *
     * <h3>Binary layout (within Instruments section)</h3>
     * <pre>
     *   +0..+3 (long)  attackOffset   – byte offset from Instruments base to attack sample
     *   +4..+7 (long)  loopOffset     – byte offset from Instruments base to loop start;
     *                                   equals nullSampleOffset when there is no loop
     *   +8..+9 (word)  attackLength   – attack sample length in words
     *   +10..+11(word) loopLength     – loop sample length in words; 2 = null sample = no loop
     *   +12..+13(word) volume         – default playback volume 0–63 (may be stored as 64 = max)
     *   +14..+15(word) totalLength    – reserved (always 0 in practice)
     *   +16..+31       name           – 16-byte ISO-8859-1 ASCII name, space-padded
     * </pre>
     *
     * <p>{@code sampleData} holds the attack bytes ({@code attackLength * 2} bytes of signed 8-bit PCM).
     * When a loop is present, {@code loopOffsetRelative > 0} gives the byte offset within
     * {@code sampleData} where the loop starts, and {@code loopLength} gives its length in words.
     * When there is no loop, {@code loopOffsetRelative == 0} and {@code loopLength == 2}
     * (pointing at the null sample).
     *
     * @param name                 instrument name (trimmed)
     * @param volume               default volume (0–63; 64 is accepted and clamped at play time)
     * @param totalLength          reserved word (written back verbatim; normally 0)
     * @param loopOffsetRelative   byte offset from sample start to loop start; 0 = no loop
     * @param loopLength           loop length in words; 2 = no loop (null sample)
     * @param sampleData           raw signed 8-bit PCM bytes for the attack (includes loop)
     */
    public record Instrument(
            String name,
            int volume,
            int totalLength,
            int loopOffsetRelative,
            int loopLength,
            byte[] sampleData
    ) {
        /** Returns {@code true} when this instrument has a real loop (not just the null sample). */
        public boolean hasLoop() {
            return loopOffsetRelative > 0 && loopLength > 2;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Song
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One song (music) entry.
     *
     * <h3>Binary layout (Music.mus header)</h3>
     * <pre>
     *   +0 (word)  voice_0_offset – byte offset from Music.mus base to voice 0 pattern list
     *   +2 (word)  voice_1_offset
     *   +4 (word)  voice_2_offset
     *   +6 (word)  voice_3_offset
     *   +8 (word)  tempo          – default tempo (not read by current AMOS code; stored here)
     *   +10(word)  free           – reserved (always 0)
     *   +12..+27   name           – 16-byte ISO-8859-1 name
     * </pre>
     *
     * @param name      song name (16 chars, may include spaces)
     * @param tempo     song tempo word (stored in bank but not used by current AMOS runtime)
     * @param sequence  4 pattern playlists (one per voice); each list is a sequence of 0-based
     *                  pattern indices, terminated by {@code 0xFFFE} (loop) or {@code 0xFFFF} (stop)
     */
    public record Song(
            String name,
            int tempo,
            List<List<Integer>> sequence
    ) {}

    // ─────────────────────────────────────────────────────────────────────────
    // Command
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * AMOS Music pattern command labels (bits 14–8 of command words with bit 15 set).
     *
     * <p>The END_PATTERN command (label 0 / {@code 0x80xx}) is handled as an implicit list
     * terminator and is never stored in a {@link VoiceItem} list.
     */
    public enum Command {
        /** {@code 0x80} — end pattern; advances to next entry in the channel's playlist. */
        END_PATTERN(0),
        /** {@code 0x81} — old slide up (not supported by current AMOS player). */
        OLD_SLIDE_UP(1),
        /** {@code 0x82} — old slide down (not supported by current AMOS player). */
        OLD_SLIDE_DOWN(2),
        /** {@code 0x83} — set channel volume (parameter 0–63). */
        SET_VOLUME(3),
        /** {@code 0x84} — stop any currently running effect. */
        STOP_EFFECT(4),
        /**
         * {@code 0x85} — repeat marker / loop.
         * Parameter 0 = set the repeat mark; non-zero = jump back to mark N times.
         */
        REPEAT(5),
        /** {@code 0x86} — Amiga low-pass filter on (equivalent to SoundTracker E01). */
        FILTER_ON(6),
        /** {@code 0x87} — Amiga low-pass filter off (equivalent to SoundTracker E00). */
        FILTER_OFF(7),
        /**
         * {@code 0x88} — set replay tempo (parameter 1–100; default 17).
         * AMOS tempo adds the value to a counter each VBL; advances when counter ≥ 100.
         */
        SET_TEMPO(8),
        /** {@code 0x89} — select instrument for subsequent notes (parameter = 0-based index). */
        SET_INSTR(9),
        /** {@code 0x8A} — arpeggio; upper nibble and lower nibble of parameter (like ST "0" cmd). */
        ARPEGGIO(10),
        /** {@code 0x8B} — tone portamento; slide pitch towards next note (like ST "3" cmd). */
        TONE_PORTAMENTO(11),
        /** {@code 0x8C} — vibrato; upper nibble = speed, lower nibble = depth (like ST "4" cmd). */
        VIBRATO(12),
        /** {@code 0x8D} — volume slide; upper nibble raises, lower nibble lowers (like ST "A" cmd). */
        VOLUME_SLIDE(13),
        /** {@code 0x8E} — portamento up; decreases period by parameter every VBL (like ST "1" cmd). */
        PORTAMENTO_UP(14),
        /** {@code 0x8F} — portamento down; increases period by parameter every VBL (like ST "2" cmd). */
        PORTAMENTO_DOWN(15),
        /**
         * {@code 0x90} — delay; channel waits this many positions before continuing
         * (each note should have a corresponding delay).
         */
        DELAY(16),
        /** {@code 0x91} — position jump; sets playlist position to parameter (like ST "B" cmd). */
        POSITION_JUMP(17);

        private final int label;

        Command(int label) { this.label = label; }

        /** The raw label number encoded in bits 14–8 of the command word. */
        public int label() { return label; }

        /** Returns the {@link Command} for the given label, or {@code null} if unknown. */
        public static Command fromLabel(int label) {
            for (var c : values()) {
                if (c.label == label) return c;
            }
            return null;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // VoiceItem
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One decoded item in a pattern voice stream.
     *
     * <p>Each item is either a <b>note</b> or a <b>command</b>:
     * <ul>
     *   <li><b>Note</b> ({@code command == null}): {@code period} is the Amiga hardware period
     *       (inversely proportional to frequency; 0 = silence). {@code duration} is the raw
     *       OldNote control word with bit 14 cleared — bits 13–8 encode per-note channel volume
     *       (0–63) and bits 7–0 encode the duration in player ticks.</li>
     *   <li><b>Command</b> ({@code command != null}): {@code command} is the {@link Command} type
     *       and {@code parameter} is its argument. {@code period} and {@code duration} are 0.</li>
     * </ul>
     *
     * <p>The {@link Command#END_PATTERN} marker ({@code 0x8000}) is <em>not</em> stored in the
     * list; it is written implicitly when the bank is serialised.
     *
     * @param period    Amiga period value for a note (0 = silence); 0 for commands
     * @param duration  OldNote control word (bit 14 cleared); 0 for commands or bare periods
     * @param command   command type; {@code null} for notes
     * @param parameter command argument; 0 for notes
     */
    public record VoiceItem(int period, int duration, Command command, int parameter) {
        /** Returns {@code true} if this item is a note (plays a period or silence). */
        public boolean isNote() { return command == null; }
        /** Returns {@code true} if this item is a command. */
        public boolean isCommand() { return command != null; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Pattern
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * One pattern: per-voice note/command streams.
     *
     * <p>The four voices are independent time streams; they are not synchronised to a shared
     * row grid (unlike tracker/MOD format). Each voice starts simultaneously when the pattern
     * is entered, but advances at its own pace determined by the {@code duration} field of
     * each {@link VoiceItem}.
     *
     * <h3>Binary layout (Patterns section)</h3>
     * <pre>
     *   Patterns:
     *     dc.w  count
     *     REPT count
     *       dc.w  voice_0_offset   ; byte offset from Patterns base
     *       dc.w  voice_1_offset
     *       dc.w  voice_2_offset
     *       dc.w  voice_3_offset
     *     ENDR
     *     ; note lists follow (at the indicated offsets)
     * </pre>
     *
     * @param voices 4 decoded voice streams; each stream is terminated by an implicit END
     */
    public record Pattern(List<List<VoiceItem>> voices) {}
}
