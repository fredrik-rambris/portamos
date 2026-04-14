/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * In-memory model of a parsed AMOS Professional Samples bank.
 *
 * <p>A Samples bank holds a table of 8-bit signed mono PCM samples, each with an
 * 8-character ASCII name and a playback frequency in Hz.
 *
 * <p>Binary format: see {@link SampleBankReader}.
 */
public record SampleBank(
        short bankNumber,
        List<Sample> samples
) implements AmosBank {

    @Override
    public Type type() {
        return Type.SAMPLES;
    }

    @Override
    public boolean chipRam() {
        return true;
    }

    @Override
    public BankWriter writer() {
        return new SampleBankWriter();
    }

    /**
     * One sample entry in the bank.
     *
     * @param name         8-character ASCII name (trailing spaces/nulls stripped on read)
     * @param frequencyHz  playback frequency in Hz
     * @param pcmData      signed 8-bit mono PCM samples
     */
    public record Sample(String name, int frequencyHz, byte[] pcmData) {
        public boolean isEmpty() {
            return pcmData == null || pcmData.length == 0;
        }
    }
}
