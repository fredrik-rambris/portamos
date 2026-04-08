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

/**
 * Serializes a {@link SpriteBank} back to the AMOS Professional {@code AmSp} binary format.
 *
 * <p>This is the inverse of {@link SpriteBankReader}; see {@link SpriteBank} for the format
 * description.
 */
public class SpriteBankWriter implements BankWriter {

    @Override
    public void write(AmosBank bank, Path dest) throws IOException {
        Files.write(dest, toBytes(bank));
    }

    @Override
    public byte[] toBytes(AmosBank bank) throws IOException {
        if (!(bank instanceof SpriteBank sb)) {
            throw new IllegalArgumentException("Not a SpriteBank: " + bank.type());
        }
        return serialize(sb);
    }

    private static byte[] serialize(SpriteBank bank) {
        // Calculate total binary size
        var size = 4 + 2; // magic + count
        for (var s : bank.sprites()) {
            size += 10; // 5 × uint16 header
            if (!s.isEmpty()) size += s.data().length;
        }
        size += 64; // 32 × uint16 palette

        var buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN);
        buf.put(bank.magic().getBytes(StandardCharsets.US_ASCII));
        buf.putShort((short) bank.sprites().size());

        for (var s : bank.sprites()) {
            buf.putShort((short) s.widthWords());
            buf.putShort((short) s.height());
            buf.putShort((short) s.planes());
            buf.putShort((short) s.hotspotX());
            buf.putShort((short) s.hotspotY());
            if (!s.isEmpty()) buf.put(s.data());
        }

        for (int i = 0; i < 32; i++) {
            buf.putShort((short) (i < bank.palette().length ? bank.palette()[i] : 0));
        }

        return buf.array();
    }
}


