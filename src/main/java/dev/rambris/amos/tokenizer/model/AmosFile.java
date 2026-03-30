package dev.rambris.amos.tokenizer.model;

import java.util.List;

/**
 * In-memory representation of an AMOS program.
 *
 * This is the central data structure that can be read from and written to
 * both AMOS binary files and ASCII source files.  Future extensions will
 * add bank data (graphics, samples, etc.) alongside the token lines.
 */
public class AmosFile {

    private final AmosVersion version;
    private final List<AmosLine> lines;

    public AmosFile(AmosVersion version, List<AmosLine> lines) {
        this.version = version;
        this.lines = List.copyOf(lines);
    }

    public AmosVersion version() {
        return version;
    }

    /** The source lines, in order, each carrying its indent level and token list. */
    public List<AmosLine> lines() {
        return lines;
    }
}
