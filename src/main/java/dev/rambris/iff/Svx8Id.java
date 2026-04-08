/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import java.nio.charset.StandardCharsets;

/**
 * Well-known IFF chunk and form type identifiers for the 8SVX audio format.
 *
 * <p>The form type "8SVX" cannot be a valid Java identifier, so it is spelled
 * {@code SVX8} in this enum. {@link #asString()} still returns {@code "8SVX"}.
 */
public enum Svx8Id implements IffId {

    FORM,
    /** The {@code 8SVX} form type. */
    SVX8("8SVX"),
    VHDR,
    BODY,
    CHAN;

    private final byte[] id;

    Svx8Id(String s) {
        this.id = s.getBytes(StandardCharsets.US_ASCII);
    }
    Svx8Id() {
        this.id = name().getBytes(StandardCharsets.US_ASCII);
    }

    @Override
    public byte[] id() {
        return id;
    }
}
