package dev.rambris.iff.exceptions;

/** Thrown when an IFF file cannot be written to the filesystem. */
public class IffWriteException extends IffException {

    public IffWriteException(String message) {
        super(message);
    }

    public IffWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
