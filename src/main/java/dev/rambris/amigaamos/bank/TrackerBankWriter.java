/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes a {@link TrackerBank} to an AMOS Professional Tracker bank binary.
 *
 * <p>This is the inverse of {@link TrackerBankReader}.
 */
public class TrackerBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (bank instanceof TrackerBank tb) {
            return AmBkCodec.build(
                    tb.bankNumber(),
                    tb.chipRam(),
                    AmosBank.Type.TRACKER.identifier(),
                    tb.modData());
        }
        throw new IllegalArgumentException("Not a TrackerBank, got: " + bank.getClass().getSimpleName());
    }
}
