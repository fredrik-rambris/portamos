/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

/**
 * In-memory representation of an ILBM image decoded by {@link IlbmCodec}.
 *
 * @param bmhd     parsed bitmap header; never {@code null}
 * @param palette  RGB color entries as {@code 0x00RRGGBB} integers, one per CMAP entry;
 *                 {@code null} if no {@code CMAP} chunk was present
 * @param camgMode Amiga viewport mode ID from {@code CAMG} chunk; {@code 0} if absent
 * @param body     decompressed interleaved planar pixel data (row-major, plane-major within
 *                 each row); {@code null} if no {@code BODY} chunk was present
 */
public record IlbmImage(
        BmhdChunk bmhd,
        int[] palette,
        int camgMode,
        byte[] body
) {}
