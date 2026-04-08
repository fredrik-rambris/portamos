/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

/**
 * Callback invoked by {@link IffReader} when a chunk with a matching ID is encountered.
 *
 * <p>The {@code chunkId} parameter is the four-character ASCII chunk identifier.
 * The {@code data} parameter contains the raw chunk payload bytes (never padded —
 * the padding byte is consumed by the reader).
 */
@FunctionalInterface
public interface ChunkHandler {

    void handle(String chunkId, byte[] data);
}
