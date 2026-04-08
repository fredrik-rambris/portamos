/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

/**
 * Write-time options for {@link IlbmCodec#write(IlbmImage, IlbmOptions...)}.
 */
public enum IlbmOptions {

    /**
     * Compress the {@code BODY} chunk with the ByteRun1 (PackBits) algorithm.
     * Without this option the body is written uncompressed.
     */
    COMPRESSION_BYTERUN1
}
