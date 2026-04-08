/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.exceptions;

/**
 * Thrown when IFF data is structurally invalid or a codec-level precondition fails.
 *
 * <p>Examples: file does not start with {@code FORM}, chunk length exceeds available
 * data, {@code BODY} appears before {@code BMHD}, unsupported compression algorithm.
 */
public class IffParseException extends IffException {

    public IffParseException(String message) {
        super(message);
    }

    public IffParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
