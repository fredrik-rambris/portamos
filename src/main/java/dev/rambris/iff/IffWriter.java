/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import dev.rambris.iff.exceptions.IffWriteException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Stateful IFF FORM writer.  Create a new instance per file to be written.
 *
 * <p>Add chunks with {@link #writeChunk}, then call {@link #toForm} to retrieve
 * the complete IFF FORM bytes, or {@link #write} to write directly to a file.
 *
 * <pre>{@code
 * byte[] iff = new IffWriter()
 *     .writeChunk(IlbmId.BMHD, bmhdBytes)
 *     .writeChunk(IlbmId.CMAP, cmapBytes)
 *     .writeChunk(IlbmId.BODY, bodyBytes)
 *     .toForm(IlbmId.ILBM);
 * }</pre>
 */
public class IffWriter {

    private final ByteArrayOutputStream chunks = new ByteArrayOutputStream();

    /**
     * Appends a chunk to the FORM being built.
     *
     * <p>A pad byte ({@code 0x00}) is written automatically when {@code data.length} is odd,
     * as required by the IFF specification.
     *
     * @return {@code this} for chaining
     */
    public IffWriter writeChunk(IffId id, byte[] data) {
        try {
            chunks.write(id.id());
            var sizeBuf = ByteBuffer.allocate(4).order(ByteOrder.BIG_ENDIAN);
            sizeBuf.putInt(data.length);
            chunks.write(sizeBuf.array());
            chunks.write(data);
            if (data.length % 2 != 0) {
                chunks.write(0); // pad to even
            }
        } catch (IOException e) {
            // ByteArrayOutputStream does not throw — this branch is unreachable in practice.
            throw new IffWriteException("Unexpected write failure for chunk " + id.asString(), e);
        }
        return this;
    }

    /**
     * Returns the complete IFF FORM as a byte array.
     *
     * @param formType the four-character FORM type (e.g. {@link IlbmId#ILBM})
     */
    public byte[] toForm(IffId formType) {
        var chunkBytes = chunks.toByteArray();
        // FORM size = 4 bytes for formType + all chunk bytes
        int formSize = 4 + chunkBytes.length;
        var buf = ByteBuffer.allocate(12 + chunkBytes.length).order(ByteOrder.BIG_ENDIAN);
        buf.put(IffId.FORM.id());
        buf.putInt(formSize);
        buf.put(formType.id());
        buf.put(chunkBytes);
        return buf.array();
    }

    /**
     * Writes the complete IFF FORM to {@code path}.
     *
     * @param formType the four-character FORM type (e.g. {@link IlbmId#ILBM})
     * @throws IffWriteException if the file cannot be written
     */
    public void write(IffId formType, Path path) {
        try {
            Files.write(path, toForm(formType));
        } catch (IOException e) {
            throw new IffWriteException("Cannot write IFF file: " + path, e);
        }
    }
}
