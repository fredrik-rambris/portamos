package dev.rambris.amos;

import dev.rambris.amos.tokenizer.model.AmosToken;
import dev.rambris.amos.tokenizer.Tokenizer;

import java.nio.file.Path;
import java.util.List;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: portamos <source.amos>");
            System.exit(1);
        }
        var tokenizer = new Tokenizer();
        List<AmosToken> tokens = tokenizer.tokenize(Path.of(args[0]));
    }
}
