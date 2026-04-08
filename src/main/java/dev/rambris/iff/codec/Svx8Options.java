/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

/**
 * Write-time options for {@link Svx8Codec#write(Svx8Sound, Svx8Options...)}.
 */
public enum Svx8Options {

    /**
     * Compress the {@code BODY} chunk with Fibonacci-delta encoding (sCmpFibDelta).
     * Without this option the body is written as raw uncompressed PCM.
     */
    COMPRESSION_FIBONACCI
}
