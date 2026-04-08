/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import dev.rambris.iff.exceptions.IffParseException;
import dev.rambris.iff.exceptions.IffReadException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Stateful IFF FORM reader.  Create a new instance per file to be parsed.
 *
 * <p>Register chunk handlers with {@link #on}, then call {@link #read(byte[])} or
 * {@link #read(Path)}.  The reader iterates over all chunks in the FORM, dispatching
 * each to the matching registered handler.  Chunks with no handler are handled
 * according to the configured {@link UnknownChunkPolicy} (default: {@link UnknownChunkPolicy#SKIP}).
 *
 * <pre>{@code
 * String formType = new IffReader()
 *     .on(IlbmId.BMHD, (id, data) -> bmhd = BmhdChunk.parse(data))
 *     .on(IlbmId.BODY, (id, data) -> body = data)
 *     .read(path);
 * }</pre>
 */
public class IffReader {

    private final Map<String, ChunkHandler> handlers = new LinkedHashMap<>();
    private UnknownChunkPolicy unknownPolicy = UnknownChunkPolicy.SKIP;
    private ChunkHandler unknownHandler;

    /**
     * Registers a handler for the given chunk ID.
     * A subsequent call with the same ID replaces the previous handler.
     *
     * @return {@code this} for chaining
     */
    public IffReader on(IffId id, ChunkHandler handler) {
        handlers.put(id.asString(), handler);
        return this;
    }

    /**
     * Sets the policy applied to chunks that have no registered handler.
     * Overrides any previously set {@linkplain #onUnknown(ChunkHandler) catch-all handler}.
     *
     * @return {@code this} for chaining
     */
    public IffReader onUnknown(UnknownChunkPolicy policy) {
        this.unknownPolicy = policy;
        this.unknownHandler = null;
        return this;
    }

    /**
     * Installs a catch-all handler invoked for any chunk with no registered handler.
     * Takes precedence over the {@link UnknownChunkPolicy}.
     *
     * @return {@code this} for chaining
     */
    public IffReader onUnknown(ChunkHandler handler) {
        this.unknownHandler = handler;
        return this;
    }

    /**
     * Reads an IFF file from {@code path} and dispatches its chunks.
     *
     * @return the FORM type string (e.g. {@code "ILBM"}, {@code "8SVX"})
     * @throws IffReadException  if the file cannot be read
     * @throws IffParseException if the data is not a valid IFF FORM
     */
    public String read(Path path) {
        try {
            return read(Files.readAllBytes(path));
        } catch (IOException e) {
            throw new IffReadException("Cannot read IFF file: " + path, e);
        }
    }

    /**
     * Parses {@code data} as an IFF FORM and dispatches each chunk to registered handlers.
     *
     * @return the FORM type string (e.g. {@code "ILBM"}, {@code "8SVX"})
     * @throws IffParseException if the data is not a valid IFF FORM
     */
    public String read(byte[] data) {
        if (data.length < 12) {
            throw new IffParseException("Too small to be an IFF file (" + data.length + " bytes)");
        }

        var buf = ByteBuffer.wrap(data).order(ByteOrder.BIG_ENDIAN);

        var magic = new byte[4];
        buf.get(magic);
        var magicStr = new String(magic, StandardCharsets.US_ASCII);
        if (!IffId.FORM.asString().equals(magicStr)) {
            throw new IffParseException("Not an IFF FORM file (found: '" + magicStr + "')");
        }

        buf.getInt(); // FORM size — we rely on the buffer bounds instead
        var formType = new byte[4];
        buf.get(formType);
        var formTypeStr = new String(formType, StandardCharsets.US_ASCII);

        while (buf.remaining() >= 8) {
            var chunkId = new byte[4];
            buf.get(chunkId);
            var chunkIdStr = new String(chunkId, StandardCharsets.US_ASCII);
            int chunkSize = buf.getInt();

            if (chunkSize < 0 || chunkSize > buf.remaining()) {
                throw new IffParseException(
                        "Invalid chunk size " + chunkSize + " for chunk '" + chunkIdStr + "'");
            }

            var chunkData = new byte[chunkSize];
            buf.get(chunkData);
            if (chunkSize % 2 != 0 && buf.hasRemaining()) {
                buf.get(); // consume pad byte
            }

            dispatch(chunkIdStr, chunkData);
        }

        return formTypeStr;
    }

    private void dispatch(String chunkId, byte[] data) {
        var handler = handlers.get(chunkId);
        if (handler != null) {
            handler.handle(chunkId, data);
            return;
        }
        if (unknownHandler != null) {
            unknownHandler.handle(chunkId, data);
            return;
        }
        if (unknownPolicy == UnknownChunkPolicy.FAIL) {
            throw new IffParseException("Unknown chunk: '" + chunkId + "'");
        }
        // SKIP: do nothing
    }
}
