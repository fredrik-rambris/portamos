package dev.rambris.amigaamos;

import dev.rambris.amigaamos.bank.*;
import dev.rambris.amigaamos.tokenizer.AmosDump;
import dev.rambris.amigaamos.tokenizer.ExtJsonGenerator;
import dev.rambris.amigaamos.tokenizer.Tokenizer;
import dev.rambris.amigaamos.tokenizer.model.AmosFile;

import java.nio.file.Path;

public class Main {

    private static final String USAGE = """
            Usage:
              portamos <source.asc> <output.amos>
                  Tokenize an ASCII AMOS source file to binary.

              portamos --dump <file.amos>
                  Dump an AMOS binary file as a human-readable token listing.

              portamos --diff <expected.amos> <actual.amos>
                  Diff two AMOS binary files at the token level.

              portamos --gen-ext-json <input.Lib> --slot <n> [--start <s>] <output.json>
                  Generate a JSON definition skeleton from an AMOS extension binary.
                  --slot   Extension slot number (0=core, 1=Music, 2=Compact, 3=Request, 6=IOPorts, …)
                  --start  Byte offset from token-table base to first entry (default: -194 for slot 0, 6 otherwise)

              portamos --disasm-bank <input.Abk> <output-dir>
                  Disassemble a Resource Bank file into its component files.
                  Writes spritesheet.png, program_NNN.amui, bank.json.

              portamos --asm-bank <input-dir> <output.Abk>
                  Assemble a Resource Bank from a directory produced by --disasm-bank.
                  Reads bank.json, the spritesheet PNG, and any program_NNN.amui files.
            """;

    public static void main(String[] args) throws Exception {
        if (args.length == 0) {
            System.err.println(USAGE);
            System.exit(1);
        }

        if (args[0].equals("--gen-ext-json")) {
            runGenExtJson(args);
        } else if (args[0].equals("--dump")) {
            runDump(args);
        } else if (args[0].equals("--diff")) {
            runDiff(args);
        } else if (args[0].equals("--disasm-bank")) {
            runDisasmBank(args);
        } else if (args[0].equals("--asm-bank")) {
            runAsmBank(args);
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
    // Dump: --dump <file.amos>
    // -------------------------------------------------------------------------

    private static void runDump(String[] args) throws Exception {
        if (args.length < 2) { System.err.println(USAGE); System.exit(1); }
        new AmosDump().dump(Path.of(args[1]), System.out);
    }

    // -------------------------------------------------------------------------
    // Diff: --diff <expected.amos> <actual.amos>
    // -------------------------------------------------------------------------

    private static void runDiff(String[] args) throws Exception {
        if (args.length < 3) { System.err.println(USAGE); System.exit(1); }
        new AmosDump().diff(Path.of(args[1]), Path.of(args[2]), System.out);
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

    // -------------------------------------------------------------------------
    // Disassemble Resource Bank: --disasm-bank <input.Abk> <output-dir>
    // -------------------------------------------------------------------------

    private static void runDisasmBank(String[] args) throws Exception {
        if (args.length < 3) { System.err.println(USAGE); System.exit(1); }
        Path inputPath = Path.of(args[1]);
        Path outDir    = Path.of(args[2]);
        System.out.printf("Reading %s ...%n", inputPath.getFileName());
        var bank = AmosBank.read(inputPath);
        if (!(bank instanceof ResourceBank resourceBank)) {
            System.err.printf("Expected a Resource bank, got: %s%n", bank.type());
            System.exit(1);
            return;
        }
        System.out.printf("Bank %d (%s, %d elements, %d texts, %d programs)%n",
                resourceBank.bankNumber(),
                resourceBank.chipRam() ? "chip" : "fast",
                resourceBank.elements().size(),
                resourceBank.texts().size(),
                resourceBank.programs().size());
        new ResourceBankExporter().export(resourceBank, outDir);
    }

    // -------------------------------------------------------------------------
    // Assemble Resource Bank: --asm-bank <input-dir> <output.Abk>
    // -------------------------------------------------------------------------

    private static void runAsmBank(String[] args) throws Exception {
        if (args.length < 3) { System.err.println(USAGE); System.exit(1); }
        Path inDir   = Path.of(args[1]);
        Path outFile = Path.of(args[2]);
        Path jsonPath = inDir.resolve("bank.json");
        System.out.printf("Reading bank from %s ...%n", jsonPath);
        var bank = new ResourceBankImporter().importFrom(jsonPath);
        System.out.printf("Bank %d (%s, %d elements, %d texts, %d programs)%n",
                bank.bankNumber(),
                bank.chipRam() ? "chip" : "fast",
                bank.elements().size(),
                bank.texts().size(),
                bank.programs().size());
        new ResourceBankWriter().write(bank, outFile);
        System.out.printf("Written %s%n", outFile);
    }

    private static void die(String msg) {
        System.err.println("Error: " + msg);
        System.err.println(USAGE);
        System.exit(1);
    }
}
