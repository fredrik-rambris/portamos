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
