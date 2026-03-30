package dev.rambris.amos.tokenizer;

import java.nio.file.Path;
import java.util.List;

/**
 * High-level tokenizer that converts AMOS ASCII source to binary format.
 * Currently the ASCII parser is not yet implemented; only binary encoding is available.
 */
public class Tokenizer {

    private final AmosVersion version;
    private final BinaryEncoder encoder = new BinaryEncoder();

    public Tokenizer() {
        this(AmosVersion.PRO_101);
    }

    public Tokenizer(AmosVersion version) {
        this.version = version;
    }

    /**
     * Tokenizes an AMOS source file from disk.
     * Not yet implemented — ASCII parser coming in a future session.
     */
    public List<AmosToken> tokenize(Path source) throws Exception {
        throw new UnsupportedOperationException("Not yet implemented - use tokenizeToBytes");
    }

    /**
     * Converts an ASCII AMOS source string to binary AMOS file bytes.
     * Not yet implemented — ASCII parser coming in a future session.
     */
    public byte[] tokenizeToBytes(String asciiSource) {
        throw new UnsupportedOperationException("ASCII parser not yet implemented");
    }
}
