/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.exceptions;

/** Thrown when an IFF file cannot be opened or read from the filesystem. */
public class IffReadException extends IffException {

    public IffReadException(String message) {
        super(message);
    }

    public IffReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
