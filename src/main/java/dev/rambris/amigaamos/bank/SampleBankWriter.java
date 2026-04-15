/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * Serializes a {@link SampleBank} to an AMOS Professional Samples bank binary.
 *
 * <p>This is the inverse of {@link SampleBankReader}. See that class for the binary layout.
 */
public class SampleBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (bank instanceof SampleBank sb) {
            return serialize(sb);
        }
        throw new IllegalArgumentException("Not a SampleBank, got: " + bank.getClass().getSimpleName());
    }

    private byte[] serialize(SampleBank bank) {
        var samples = bank.samples();
        int n = samples.size();

        // Compute payload size: 2 (count) + 4n (offsets) + sum of 14+pcmLen per sample
        int payloadSize = 2 + 4 * n;
        for (var s : samples) {
            payloadSize += 14 + s.pcmData().length;
        }

        var payload = ByteBuffer.allocate(payloadSize).order(ByteOrder.BIG_ENDIAN);

        // Write count
        payload.putShort((short) n);

        // Compute and write offsets (relative to payload[0] = position of count field)
        // First sample starts at payload[2 + 4n]
        var offsets = new int[n];
        int pos = 2 + 4 * n;
        for (int i = 0; i < n; i++) {
            offsets[i] = pos;
            pos += 14 + samples.get(i).pcmData().length;
        }
        for (var o : offsets) {
            payload.putInt(o);
        }

        // Write sample entries
        for (var sample : samples) {
            payload.put(paddedName(sample.name()));
            payload.putShort((short) sample.frequencyHz());
            payload.putInt(sample.pcmData().length);
            payload.put(sample.pcmData());
        }

        return AmBkCodec.build(
                bank.bankNumber(),
                bank.chipRam(),
                AmosBank.Type.SAMPLES.identifier(),
                payload.array());
    }

    /** Returns an 8-byte space-padded ASCII name. */
    private static byte[] paddedName(String name) {
        var out = new byte[8];
        Arrays.fill(out, (byte) ' ');
        var nameBytes = name.getBytes(StandardCharsets.ISO_8859_1);
        System.arraycopy(nameBytes, 0, out, 0, Math.min(nameBytes.length, 8));
        return out;
    }
}
