/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

/**
 * Thrown when a bank operation is asked to handle a feature this codebase does not (yet)
 * implement — e.g. HAM/Extra-HalfBrite Pac.Pic import or export.
 */
public class NotSupportedException extends RuntimeException {

    public NotSupportedException(String message) {
        super(message);
    }
}
