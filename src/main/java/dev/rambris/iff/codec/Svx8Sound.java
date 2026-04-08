/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.codec;

import java.util.Arrays;

/**
 * In-memory representation of an 8SVX sound decoded by {@link Svx8Codec}.
 *
 * <h3>Channel layout</h3>
 * <p>The {@code chan} field carries the value of the optional {@code CHAN} chunk
 * (see constants below). When absent it is {@link #CHAN_MONO}.
 *
 * <p>For stereo ({@code chan == CHAN_STEREO}), the {@code BODY} chunk — and therefore
 * {@code pcmData} — contains <em>all LEFT samples followed by all RIGHT samples</em>.
 * The two halves are always equal in length, so the split is simply at
 * {@code pcmData.length / 2}. Use {@link #leftChannel()} and {@link #rightChannel()}
 * to obtain each half. No interleaving is performed by the codec.
 *
 * <p>{@code pcmData} is always decompressed (signed 8-bit samples), regardless of
 * the {@code sCompression} value in {@code vhdr}.
 *
 * @param vhdr    parsed voice header; never {@code null}
 * @param chan    channel assignment from the {@code CHAN} chunk; {@link #CHAN_MONO} if absent
 * @param pcmData decompressed signed 8-bit PCM samples; for stereo: LEFT block then RIGHT block
 */
public record Svx8Sound(
        VhdrChunk vhdr,
        int chan,
        byte[] pcmData
) {

    /** No {@code CHAN} chunk present; treat as mono. */
    public static final int CHAN_MONO   = 0;
    /** Intended for the left  speaker (Amiga channels 0 and 3). */
    public static final int CHAN_LEFT   = 2;
    /** Intended for the right speaker (Amiga channels 1 and 2). */
    public static final int CHAN_RIGHT  = 4;
    /**
     * Stereo pair: {@code pcmData} contains the LEFT block (first half)
     * followed by the RIGHT block (second half), each of equal length.
     */
    public static final int CHAN_STEREO = 6;

    /** {@code true} when this sound was recorded with two channels. */
    public boolean stereo() {
        return chan == CHAN_STEREO;
    }

    /**
     * Returns the left-channel samples.
     * For mono or left-only sounds this returns all of {@code pcmData}.
     * For stereo this returns the first half of {@code pcmData}.
     */
    public byte[] leftChannel() {
        if (chan != CHAN_STEREO) return pcmData;
        return Arrays.copyOfRange(pcmData, 0, pcmData.length / 2);
    }

    /**
     * Returns the right-channel samples.
     * For mono or right-only sounds this returns all of {@code pcmData}.
     * For stereo this returns the second half of {@code pcmData}.
     */
    public byte[] rightChannel() {
        if (chan != CHAN_STEREO) return pcmData;
        return Arrays.copyOfRange(pcmData, pcmData.length / 2, pcmData.length);
    }
}
