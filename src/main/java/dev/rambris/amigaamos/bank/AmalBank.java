/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.ArrayList;
import java.util.List;

/**
 * In-memory model of a parsed AMOS Professional AMAL bank.
 *
 * <p>An AMAL bank holds two types of data:
 * <ul>
 *   <li><b>Movements</b>: recorded series of object X/Y position deltas for the PLay command.</li>
 *   <li><b>Programs</b>: AMAL command strings in ASCII, with {@code ~} as line separator.</li>
 * </ul>
 *
 * <p>Binary format: see {@link AmalBankReader}.
 */
public record AmalBank(
        short bankNumber,
        boolean chipRam,
        List<Movement> movements,
        List<String> programs,
        String environment
) implements AmosBank {

    @Override
    public Type type() {
        return Type.AMAL;
    }

    @Override
    public BankWriter writer() { return new AmalBankWriter(); }

    // -------------------------------------------------------------------------
    // Movement model
    // -------------------------------------------------------------------------

    /**
     * One entry in the AMAL movement table.
     *
     * <p>{@code xMove} and {@code yMove} may be {@code null} if the movement has no data.
     *
     * @param name   8-character ASCII name (trailing spaces stripped)
     * @param xMove  X-axis movement data, or {@code null}
     * @param yMove  Y-axis movement data, or {@code null}
     */
    public record Movement(String name, MovementData xMove, MovementData yMove) {
        public boolean isEmpty() {
            return xMove == null && yMove == null;
        }
    }

    /**
     * Movement data for one axis.
     *
     * <p>Raw bytes are preserved for byte-identical binary round-trips. Use
     * {@link #instructions()} to decode the step sequence for display or JSON export.
     *
     * <p>For X-axis raw data: starts with a {@code 0x00} backward-playback sentinel followed by
     * step bytes terminated by {@code 0x00}. Total length is {@code n_x} as stored in the header.
     *
     * <p>For Y-axis raw data: step bytes terminated by {@code 0x00}, with optional alignment padding.
     *
     * @param speed  recording speed in 1/50 sec intervals per step
     * @param raw    raw step bytes from the file (or constructed from instructions)
     */
    public record MovementData(int speed, byte[] raw) {

        /**
         * Decodes the raw bytes into a list of instructions.
         *
         * <p>Skips a single leading {@code 0x00} byte if present (backward-playback sentinel for
         * X-axis data), then decodes step bytes until the first {@code 0x00} terminator.
         */
        public List<Instruction> instructions() {
            return decodeRaw(raw);
        }

        /**
         * Builds a {@link MovementData} from decoded instructions for X-axis storage.
         *
         * <p>The resulting raw bytes have the format:
         * {@code 0x00 (sentinel) + encoded instructions + 0x00 (terminator)}.
         */
        public static MovementData fromXInstructions(int speed, List<Instruction> instructions) {
            byte[] encoded = encodeInstructions(instructions);
            byte[] raw = new byte[1 + encoded.length + 1];
            // raw[0] = 0x00 (backward-playback sentinel)
            System.arraycopy(encoded, 0, raw, 1, encoded.length);
            // raw[last] = 0x00 (end-of-forward terminator)
            return new MovementData(speed, raw);
        }

        /**
         * Builds a {@link MovementData} from decoded instructions for Y-axis storage.
         *
         * <p>The resulting raw bytes have the format:
         * {@code encoded instructions + 0x00 (terminator) [+ 0x00 alignment pad if odd]}.
         */
        public static MovementData fromYInstructions(int speed, List<Instruction> instructions) {
            byte[] encoded = encodeInstructions(instructions);
            int padded = (encoded.length + 1 + 1) & ~1; // even-pad: instructions + terminator
            byte[] raw = new byte[padded];
            System.arraycopy(encoded, 0, raw, 0, encoded.length);
            // raw[encoded.length] = 0x00 (terminator); raw[padded-1] = 0x00 if padded
            return new MovementData(speed, raw);
        }

        // -------------------------------------------------------------------------
        // Helpers
        // -------------------------------------------------------------------------

        private static List<Instruction> decodeRaw(byte[] data) {
            var instructions = new ArrayList<Instruction>();
            int start = 0;
            if (data.length > 0 && (data[0] & 0xFF) == 0x00) start = 1;
            for (int i = start; i < data.length; i++) {
                int v = data[i] & 0xFF;
                if (v == 0x00) break;
                if ((v & 0x80) != 0) {
                    instructions.add(new Instruction.Wait(v & 0x7F));
                } else {
                    int delta = (v & 0x40) != 0 ? v - 128 : v;
                    instructions.add(new Instruction.Delta(delta));
                }
            }
            return instructions;
        }

        private static byte[] encodeInstructions(List<Instruction> instructions) {
            var out = new byte[instructions.size()];
            for (int i = 0; i < instructions.size(); i++) {
                out[i] = switch (instructions.get(i)) {
                    case Instruction.Wait w -> (byte) (0x80 | (w.ticks() & 0x7F));
                    case Instruction.Delta d -> (byte) (d.pixels() & 0x7F);
                };
            }
            return out;
        }
    }

    /**
     * One decoded instruction in a movement sequence.
     */
    public sealed interface Instruction {

        /**
         * Pause movement for {@code ticks} intervals (1/50 sec each).
         * Encoded as {@code 0x80..0xFF}: ticks = {@code byte & 0x7F}.
         */
        record Wait(int ticks) implements Instruction {}

        /**
         * Move by {@code pixels} pixels (signed, 7-bit two's complement).
         * Encoded as {@code 0x01..0x7F}: delta = 7-bit signed value.
         * Positive (0x01–0x3F) = move right/down; negative (0x40–0x7F) = move left/up.
         */
        record Delta(int pixels) implements Instruction {}
    }
}
