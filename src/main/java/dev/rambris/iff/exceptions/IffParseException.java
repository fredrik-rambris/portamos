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
