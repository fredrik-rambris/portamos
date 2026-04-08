/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer.model;

import dev.rambris.amigaamos.bank.AmosBank;

import java.util.List;

/**
 * In-memory representation of an AMOS program, including any attached data banks.
 */
public class AmosFile {

    private final AmosVersion version;
    private final List<AmosLine> lines;
    private final List<AmosBank> banks;

    public AmosFile(AmosVersion version, List<AmosLine> lines) {
        this(version, lines, List.of());
    }

    public AmosFile(AmosVersion version, List<AmosLine> lines, List<AmosBank> banks) {
        this.version = version;
        this.lines   = List.copyOf(lines);
        this.banks   = List.copyOf(banks);
    }

    public AmosVersion version() { return version; }

    /** The source lines, in order, each carrying its indent level and token list. */
    public List<AmosLine> lines() { return lines; }

    /** Banks attached to this program (empty if none). */
    public List<AmosBank> banks() { return banks; }

    /** Returns a new {@code AmosFile} with the given banks, keeping version and lines. */
    public AmosFile withBanks(List<AmosBank> banks) {
        return new AmosFile(version, lines, banks);
    }
}
