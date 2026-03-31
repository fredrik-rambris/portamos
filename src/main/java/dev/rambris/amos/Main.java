package dev.rambris.amos;

import dev.rambris.amos.tokenizer.ExtJsonGenerator;
import dev.rambris.amos.tokenizer.Tokenizer;
import dev.rambris.amos.tokenizer.model.AmosFile;

import java.nio.file.Path;

public class Main {

    private static final String USAGE = """
            Usage:
              portamos <source.asc> <output.amos>
                  Tokenize an ASCII AMOS source file to binary.

              portamos --gen-ext-json <input.Lib> --slot <n> [--start <s>] <output.json>
                  Generate a JSON definition skeleton from an AMOS extension binary.
                  --slot   Extension slot number (0=core, 1=Music, 2=Compact, 3=Request, 6=IOPorts, …)
                  --start  Byte offset from token-table base to first entry (default: -194 for slot 0, 6 otherwise)
            """;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println(USAGE);
            System.exit(1);
        }

        if (args[0].equals("--gen-ext-json")) {
            runGenExtJson(args);
        } else {
            runTokenize(args);
        }
    }

    // -------------------------------------------------------------------------
    // Tokenize: <source.asc> <output.amos>
    // -------------------------------------------------------------------------

    private static void runTokenize(String[] args) throws Exception {
        if (args.length < 2) {
            System.err.println(USAGE);
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

    // -------------------------------------------------------------------------
    // Generate extension JSON: --gen-ext-json <input.Lib> --slot <n> [--start <s>] <output.json>
    // -------------------------------------------------------------------------

    private static void runGenExtJson(String[] args) throws Exception {
        Path inputPath = null;
        Path outputPath = null;
        int slot = -1;
        int start = Integer.MIN_VALUE; // sentinel = not set

        int i = 1;
        while (i < args.length) {
            switch (args[i]) {
                case "--slot" -> {
                    if (++i >= args.length) die("--slot requires a value");
                    slot = Integer.parseInt(args[i]);
                }
                case "--start" -> {
                    if (++i >= args.length) die("--start requires a value");
                    start = Integer.parseInt(args[i]);
                }
                default -> {
                    if (inputPath == null) inputPath = Path.of(args[i]);
                    else if (outputPath == null) outputPath = Path.of(args[i]);
                    else die("Unexpected argument: " + args[i]);
                }
            }
            i++;
        }

        if (inputPath == null) die("Missing input .Lib path");
        if (outputPath == null) die("Missing output .json path");
        if (slot < 0) die("--slot is required");
        if (start == Integer.MIN_VALUE) start = (slot == 0) ? -194 : 6;

        System.out.printf("Generating JSON from %s (slot=%d, start=%d)%n", inputPath.getFileName(), slot, start);
        ExtJsonGenerator.generate(inputPath, slot, start, outputPath);
    }

    private static void die(String msg) {
        System.err.println("Error: " + msg);
        System.err.println(USAGE);
        System.exit(1);
    }
}
