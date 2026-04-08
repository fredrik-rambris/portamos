/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import dev.rambris.iff.exceptions.IffParseException;
import org.junit.jupiter.api.Test;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class IffReaderTest {

    // -------------------------------------------------------------------------
    // Helpers for building minimal IFF data
    // -------------------------------------------------------------------------

    /** Builds a minimal FORM IFF with the given chunks. */
    private static byte[] buildForm(String formType, byte[]... chunkBlocks) {
        int totalChunkBytes = 0;
        for (byte[] cb : chunkBlocks) totalChunkBytes += cb.length;

        // FORM size = 4 (type) + all chunk bytes
        int formSize = 4 + totalChunkBytes;
        var buf = ByteBuffer.allocate(12 + totalChunkBytes).order(ByteOrder.BIG_ENDIAN);
        buf.put("FORM".getBytes(StandardCharsets.US_ASCII));
        buf.putInt(formSize);
        buf.put(formType.getBytes(StandardCharsets.US_ASCII));
        for (byte[] cb : chunkBlocks) buf.put(cb);
        return buf.array();
    }

    /** Encodes a single chunk (id + size + data, padded to even). */
    private static byte[] chunk(String id, byte[] data) {
        int padded = data.length + (data.length % 2);
        var buf = ByteBuffer.allocate(8 + padded).order(ByteOrder.BIG_ENDIAN);
        buf.put(id.getBytes(StandardCharsets.US_ASCII));
        buf.putInt(data.length);
        buf.put(data);
        return buf.array();
    }

    private static byte[] chunk(String id, String text) {
        return chunk(id, text.getBytes(StandardCharsets.US_ASCII));
    }

    // -------------------------------------------------------------------------
    // Tests
    // -------------------------------------------------------------------------

    @Test
    void unknownChunk_defaultPolicySkips() {
        byte[] iff = buildForm("TEST",
                chunk("KNOW", "hello"),
                chunk("UNKN", "ignored"));

        List<String> seen = new ArrayList<>();
        String formType = new IffReader()
                .on(FourCC.of("KNOW"), (id, d) -> seen.add(id))
                .read(iff);

        assertEquals("TEST", formType);
        assertEquals(List.of("KNOW"), seen);
    }

    @Test
    void unknownChunk_failPolicyThrows() {
        byte[] iff = buildForm("TEST",
                chunk("KNOW", "hello"),
                chunk("UNKN", "bad"));

        var reader = new IffReader()
                .on(FourCC.of("KNOW"), (id, d) -> {})
                .onUnknown(UnknownChunkPolicy.FAIL);

        assertThrows(IffParseException.class, () -> reader.read(iff));
    }

    @Test
    void unknownChunk_customHandlerReceivesIdAndData() {
        byte[] payload = {1, 2, 3};
        byte[] iff = buildForm("TEST", chunk("CUST", payload));

        List<String>   ids  = new ArrayList<>();
        List<byte[]>   datas = new ArrayList<>();

        new IffReader()
                .onUnknown((id, d) -> { ids.add(id); datas.add(d); })
                .read(iff);

        assertEquals(List.of("CUST"), ids);
        assertArrayEquals(payload, datas.get(0));
    }

    @Test
    void knownChunk_handlerCalledWithCorrectData() {
        byte[] payload = {0x01, 0x02, 0x03, 0x04};
        byte[] iff = buildForm("DEMO", chunk("DATA", payload));

        byte[][] captured = {null};
        new IffReader()
                .on(FourCC.of("DATA"), (id, d) -> captured[0] = d)
                .read(iff);

        assertArrayEquals(payload, captured[0]);
    }

    @Test
    void notFormFile_throwsIffParseException() {
        byte[] notIff = "This is not an IFF file at all!".getBytes(StandardCharsets.US_ASCII);
        assertThrows(IffParseException.class, () -> new IffReader().read(notIff));
    }

    @Test
    void tooSmall_throwsIffParseException() {
        assertThrows(IffParseException.class, () -> new IffReader().read(new byte[5]));
    }

    @Test
    void paddingByteSkippedForOddChunks() {
        // Build a form with two chunks: first has odd payload (needs 1 pad byte),
        // second should still be found correctly.
        byte[] first  = {0x01, 0x02, 0x03}; // 3 bytes odd
        byte[] second = {0x0A, 0x0B};

        byte[] iff = buildForm("TPAD",
                chunk("ODD1", first),
                chunk("EVEN", second));

        byte[][] cap2 = {null};
        new IffReader()
                .on(FourCC.of("EVEN"), (id, d) -> cap2[0] = d)
                .read(iff);

        assertArrayEquals(second, cap2[0]);
    }

    @Test
    void formTypeIsReturned() {
        byte[] iff = buildForm("ILBM");
        assertEquals("ILBM", new IffReader().read(iff));
    }
}
