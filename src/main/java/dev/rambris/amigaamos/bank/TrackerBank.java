/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

/**
 * In-memory model of an AMOS Professional Tracker bank.
 *
 * <p>A Tracker bank is a thin AmBk wrapper around a ProTracker MOD file,
 * loaded via the AMOS {@code Track Load} instruction.  The payload bytes
 * are a complete, self-contained MOD file and are treated as opaque data.
 *
 * <p>Binary format: see {@link TrackerBankReader}.
 */
public record TrackerBank(
        short bankNumber,
        boolean chipRam,
        byte[] modData
) implements AmosBank {

    @Override
    public Type type() {
        return Type.TRACKER;
    }

    @Override
    public BankWriter writer() {
        return new TrackerBankWriter();
    }
}
