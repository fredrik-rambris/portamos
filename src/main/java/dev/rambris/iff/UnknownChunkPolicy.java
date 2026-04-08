/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff;

import dev.rambris.iff.exceptions.IffParseException;

/**
 * Controls how {@link IffReader} handles chunks that have no registered handler.
 */
public enum UnknownChunkPolicy {

    /**
     * Silently skip unrecognised chunks (default).
     */
    SKIP,

    /**
     * Throw {@link IffParseException} when an unrecognised chunk is encountered.
     */
    FAIL
}
