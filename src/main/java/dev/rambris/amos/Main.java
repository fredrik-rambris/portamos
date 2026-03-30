package dev.rambris.amos;

import dev.rambris.amos.tokenizer.Tokenizer;
import dev.rambris.amos.tokenizer.model.AmosFile;

import java.nio.file.Path;

public class Main {
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println("Usage: portamos <source.asc> <output.amos>");
            System.exit(1);
        }
        System.out.println("Reading " + args[0]);
        var tokenizer = new Tokenizer();
        AmosFile amosFile = tokenizer.parse(Path.of(args[0]));
        System.out.println("Encoding");
        byte[] binary = tokenizer.encode(amosFile);
        System.out.println("Writing " + args[1]);
        java.nio.file.Files.write(Path.of(args[1]), binary);
    }
}
