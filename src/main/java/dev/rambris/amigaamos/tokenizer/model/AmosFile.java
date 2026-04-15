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

    /**
     * Raw compiled-procedure body, present when the source was processed by the
     * AMOSPro Compiler.  Starts with the sentinel byte (0x00) that terminates
     * the line-walker, followed by the m68k machine-code bytes.  {@code null}
     * when the program has no compiled procedures.
     */
    private final byte[] compiledBody;

    public AmosFile(AmosVersion version, List<AmosLine> lines) {
        this(version, lines, List.of(), null);
    }

    public AmosFile(AmosVersion version, List<AmosLine> lines, List<AmosBank> banks) {
        this(version, lines, banks, null);
    }

    public AmosFile(AmosVersion version, List<AmosLine> lines, List<AmosBank> banks,
                    byte[] compiledBody) {
        this.version = version;
        this.lines = List.copyOf(lines);
        this.banks = List.copyOf(banks);
        this.compiledBody = compiledBody;
    }

    public AmosVersion version() { return version; }

    /** The source lines, in order, each carrying its indent level and token list. */
    public List<AmosLine> lines() { return lines; }

    /** Banks attached to this program (empty if none). */
    public List<AmosBank> banks() { return banks; }

    /**
     * The raw compiled-procedure body (sentinel + m68k code), or {@code null} if absent.
     * Written verbatim into the code section after the encoded lines by
     * {@link dev.rambris.amigaamos.tokenizer.AmosFileWriter}.
     */
    public byte[] compiledBody() {
        return compiledBody;
    }

    /**
     * Returns {@code true} if this program has a compiled procedure body.
     */
    public boolean hasCompiledBody() {
        return compiledBody != null && compiledBody.length > 0;
    }

    /**
     * Returns a new {@code AmosFile} with the given banks, keeping version, lines, and compiled body.
     */
    public AmosFile withBanks(List<AmosBank> banks) {
        return new AmosFile(version, lines, banks, compiledBody);
    }
}
