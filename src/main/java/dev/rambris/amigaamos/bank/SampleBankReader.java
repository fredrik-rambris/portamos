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
import java.util.ArrayList;
import java.util.List;

/**
 * Parses an AMOS Professional Samples bank ({@code .Abk} with type {@code "Samples "}).
 *
 * <p>Binary layout (all big-endian):
 * <pre>
 *   [4]  "AmBk"
 *   [2]  bank number
 *   [2]  flags (0x0000 = chip RAM, 0x0001 = fast RAM)
 *   [4]  length (bit 31 may be set by the saver; masked out)
 *   [8]  "Samples "
 *   --- Payload (offset 0 = position of sample count) ---
 *   [2]  n  — number of samples
 *   [4×n] offsets — byte offsets from payload[0] to each sample entry
 *   --- Sample entries ---
 *   [8]  name  — ASCII, trailing bytes may contain garbage; we strip nulls/spaces
 *   [2]  frequencyHz  — playback rate in Hz (uint16)
 *   [4]  length       — PCM data size in bytes (uint32)
 *   [length] pcmData  — signed 8-bit mono PCM
 * </pre>
 */
public class SampleBankReader {

    public static SampleBank read(Path path) throws IOException {
        return read(Files.readAllBytes(path));
    }

    public static SampleBank read(byte[] raw) throws IOException {
        var hdr = AmBkCodec.parse(raw);
        return parse(hdr.bankNumber(), hdr.payload());
    }

    private static SampleBank parse(short bankNumber, byte[] payload)
            throws IOException {
        if (payload.length < 2) {
            throw new IOException("Samples payload too small");
        }

        var buf = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
        int n = buf.getShort() & 0xFFFF;

        if (payload.length < 2 + 4L * n) {
            throw new IOException("Samples payload truncated in offset table");
        }

        var offsets = new int[n];
        for (int i = 0; i < n; i++) {
            offsets[i] = buf.getInt();
        }

        var samples = new ArrayList<SampleBank.Sample>(n);
        for (int i = 0; i < n; i++) {
            int off = offsets[i];
            if (off + 14 > payload.length) {
                throw new IOException("Sample " + i + " offset " + off + " out of range");
            }
            var name = parseName(payload, off);
            int freq = ((payload[off + 8] & 0xFF) << 8) | (payload[off + 9] & 0xFF);
            long len = ((payload[off + 10] & 0xFFL) << 24)
                     | ((payload[off + 11] & 0xFFL) << 16)
                     | ((payload[off + 12] & 0xFFL) << 8)
                     |  (payload[off + 13] & 0xFFL);
            if (off + 14 + len > payload.length) {
                throw new IOException("Sample " + i + " data extends past end of payload");
            }
            var pcm = new byte[(int) len];
            System.arraycopy(payload, off + 14, pcm, 0, (int) len);
            samples.add(new SampleBank.Sample(name, freq, pcm));
        }

        return new SampleBank(bankNumber, List.copyOf(samples));
    }

    /** Reads 8 name bytes and strips trailing nulls, spaces, and non-printable characters. */
    private static String parseName(byte[] payload, int off) {
        var raw = new byte[8];
        System.arraycopy(payload, off, raw, 0, 8);
        // Find the last printable ASCII character
        int end = 8;
        while (end > 0 && (raw[end - 1] == 0 || raw[end - 1] == ' ' || raw[end - 1] < 0x20)) {
            end--;
        }
        return new String(raw, 0, end, StandardCharsets.ISO_8859_1);
    }
}
