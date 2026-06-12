/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Serializes a {@link BankSet} to the {@code AmBs} binary format.
 */
public class BankSetWriter {

    public void write(BankSet bankSet, Path dest) throws IOException {
        Files.write(dest, toBytes(bankSet));
    }

    public byte[] toBytes(BankSet bankSet) throws IOException {
        var body = new ByteArrayOutputStream();

        for (var bank : bankSet.banks()) {
            body.write(bank.writer().toBytes(bank));
        }

        var bodyBytes = body.toByteArray();
        var buf = ByteBuffer.allocate(6 + bodyBytes.length).order(ByteOrder.BIG_ENDIAN);
        buf.put("AmBs".getBytes(StandardCharsets.US_ASCII));
        buf.putShort((short) bankSet.banks().size());
        buf.put(bodyBytes);
        return buf.array();
    }
}
