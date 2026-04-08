/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

/**
 * In-memory representation of an AMOS Professional Work or Data bank.
 *
 * <p>Both bank types share the same binary layout; the {@link #type()} field
 * ({@link AmosBank.Type#WORK} or {@link AmosBank.Type#DATA}) distinguishes them.
 * Use the static factory methods for convenient construction:
 *
 * <pre>
 *   RawBank.Work(bankNumber, data)      // WORK bank, fast RAM
 *   RawBank.ChipWork(bankNumber, data)  // WORK bank, chip RAM
 *   RawBank.Data(bankNumber, data)      // DATA bank, fast RAM
 *   RawBank.ChipData(bankNumber, data)  // DATA bank, chip RAM
 * </pre>
 *
 * <p>Binary format: see {@link RawBankReader}.
 */
public record RawBank(AmosBank.Type type, short bankNumber, boolean chipRam, byte[] data)
        implements AmosBank {

    @Override
    public BankWriter writer() { return new RawBankWriter(); }

    // -------------------------------------------------------------------------
    // Factory methods
    // -------------------------------------------------------------------------

    public static RawBank Work(short bankNumber, byte[] data) {
        return new RawBank(AmosBank.Type.WORK, bankNumber, false, data);
    }

    public static RawBank ChipWork(short bankNumber, byte[] data) {
        return new RawBank(AmosBank.Type.WORK, bankNumber, true, data);
    }

    public static RawBank Data(short bankNumber, byte[] data) {
        return new RawBank(AmosBank.Type.DATA, bankNumber, false, data);
    }

    public static RawBank ChipData(short bankNumber, byte[] data) {
        return new RawBank(AmosBank.Type.DATA, bankNumber, true, data);
    }
}
