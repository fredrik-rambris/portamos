package dev.rambris.amigaamos.tokenizer;

/**
 * Thrown when a tokenize or encode error occurs on a specific source line.
 *
 * <p>{@link #lineNumber()} is 1-based.  {@link #column()} is 1-based when known,
 * or {@code -1} when only the line can be identified.  {@link #sourceLine()} is
 * the raw source text of the offending line, or {@code null} when not available
 * (e.g. errors that originate during binary encoding rather than parsing).
 */
public class TokenizeException extends RuntimeException {

    private final int lineNumber;
    private final int column;
    private final String sourceLine;

    public TokenizeException(int lineNumber, int column, String sourceLine, String message) {
        super(formatMessage(lineNumber, column, sourceLine, message));
        this.lineNumber = lineNumber;
        this.column = column;
        this.sourceLine = sourceLine;
    }

    public TokenizeException(int lineNumber, int column, String sourceLine, Throwable cause) {
        super(formatMessage(lineNumber, column, sourceLine, cause.getMessage()), cause);
        this.lineNumber = lineNumber;
        this.column = column;
        this.sourceLine = sourceLine;
    }

    /** 1-based line number in the source file. */
    public int lineNumber() { return lineNumber; }

    /** 1-based column, or {@code -1} if the position within the line is not known. */
    public int column() { return column; }

    /** The raw source text of the offending line, or {@code null} during binary encoding. */
    public String sourceLine() { return sourceLine; }

    private static String formatMessage(int line, int col, String sourceLine, String detail) {
        StringBuilder sb = new StringBuilder();
        sb.append("Line ").append(line);
        if (col >= 1) sb.append(", column ").append(col);
        if (detail != null) sb.append(": ").append(detail);
        if (sourceLine != null) sb.append("\n  ").append(sourceLine);
        return sb.toString();
    }
}
