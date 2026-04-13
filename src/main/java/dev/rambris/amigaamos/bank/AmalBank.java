/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

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
        List<String> programs
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
     * <p>{@code xMove} and {@code yMove} may be {@code null} if the movement is not defined
     * (i.e. the offset in the bank is zero or points outside the movement data area).
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
     * Decoded movement data for one axis.
     *
     * @param speed        recording speed in 1/50 sec intervals
     * @param instructions decoded sequence of instructions
     */
    public record MovementData(int speed, List<Instruction> instructions) {}

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
