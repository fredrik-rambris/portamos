/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.exceptions;

/** Base class for all IFF library runtime exceptions. */
public class IffException extends RuntimeException {

    public IffException(String message) {
        super(message);
    }

    public IffException(String message, Throwable cause) {
        super(message, cause);
    }
}
