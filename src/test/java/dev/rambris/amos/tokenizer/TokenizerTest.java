package dev.rambris.amos.tokenizer;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class TokenizerTest {

    @Test
    void placeholder() {
        assertNotNull(new Tokenizer());
    }

    @Test
    @Disabled("ASCII parser not yet implemented")
    void tokenize_numbers() throws Exception {
        // Will compare output against Numbers.AMOS when the ASCII parser is ready
    }
}
