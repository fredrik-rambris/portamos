package dev.rambris.amos.tokenizer;

public record Token(TokenType type, String value, int line) {
}
