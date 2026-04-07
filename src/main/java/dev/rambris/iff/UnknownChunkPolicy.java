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
