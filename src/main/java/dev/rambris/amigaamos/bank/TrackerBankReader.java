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
 * Reads an AMOS Professional Tracker bank ({@code .Abk} with type {@code "Tracker "}).
 *
 * <p>The entire AmBk payload is a ProTracker MOD file.
 */
public class TrackerBankReader {

    public static TrackerBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static TrackerBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        return new TrackerBank(hdr.bankNumber(), hdr.chipRam(), hdr.payload());
    }
}
