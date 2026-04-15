/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.rambris.amigaamos.bank.*;
import dev.rambris.amigaamos.tokenizer.AmosDump;
import dev.rambris.amigaamos.tokenizer.ExtJsonGenerator;
import dev.rambris.amigaamos.tokenizer.Tokenizer;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;

@Command(
        name = "portamos",
        mixinStandardHelpOptions = true,
        version = {
                "portamos " + Version.VALUE,
                "Copyright 2026 Fredrik Rambris",
                "License: Apache 2.0 <https://www.apache.org/licenses/LICENSE-2.0>"
        },
        description = {
                "AMOS Professional tokenizer and bank tool.",
                "",
                "Run 'portamos dev-help' for developer/diagnostic commands."
        },
        footer = {
                "",
                "Copyright 2026 Fredrik Rambris. License: Apache 2.0",
                "<https://www.apache.org/licenses/LICENSE-2.0>"
        },
        subcommands = {
                Main.BuildCommand.class,
                Main.ListCommand.class,
                Main.DisasmCommand.class,
                Main.AsmCommand.class,
                Main.RawCommand.class,
                Main.DevHelpCommand.class,
                Main.DumpCommand.class,
                Main.DiffCommand.class,
                Main.GenExtJsonCommand.class,
                CommandLine.HelpCommand.class
        }
)
public class Main implements Callable<Integer> {

    public static void main(String[] args) {
        System.exit(new CommandLine(new Main()).execute(args));
    }

    @Override
    public Integer call() {
        CommandLine.usage(this, System.out);
        return 0;
    }

    // =========================================================================
    // build
    // =========================================================================

    @Command(
            name = "build",
            mixinStandardHelpOptions = true,
            description = {
                    "Tokenize an ASCII AMOS source file and write a binary .AMOS file.",
                    "Banks can be attached from disk Abk files or assembled from JSON + data files."
            }
    )
    static class BuildCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<source.Asc>",
                description = "ASCII AMOS source file to tokenize")
        Path source;

        @Parameters(index = "1", paramLabel = "<output.AMOS>",
                description = "Output binary AMOS file")
        Path output;

        @Option(names = "--add-bank", paramLabel = "<path.Abk>",
                description = "Attach a bank loaded directly from an Abk file (repeatable)")
        List<Path> addBanks = new ArrayList<>();

        @Option(names = "--import-bank", paramLabel = "<path.json>",
                description = "Assemble a bank from JSON + data files and attach it (repeatable)")
        List<Path> importBanks = new ArrayList<>();

        @Option(names = "--definition", paramLabel = "<path.json>",
                description = "Load an additional extension definition JSON file (repeatable). "
                              + "Use this for third-party extensions not included in the built-in set.")
        List<Path> definitions = new ArrayList<>();

        @Option(names = "--fold",
                description = "Mark all Procedure blocks as folded in the AMOS editor by default.")
        boolean fold = false;

        @Override
        public Integer call() throws Exception {
            System.out.println("Reading " + source);
            var tokenizer = new Tokenizer();
            for (var defPath : definitions) {
                System.out.println("Loading definition " + defPath);
                tokenizer.withDefinition(defPath);
            }
            if (fold) tokenizer.withFoldedProcedures();
            var amosFile = tokenizer.parse(source);

            var banks = new ArrayList<AmosBank>();

            for (var bankPath : addBanks) {
                System.out.println("Adding bank from " + bankPath);
                banks.add(AmosBank.read(bankPath));
            }

            for (var jsonPath : importBanks) {
                System.out.println("Importing bank from " + jsonPath);
                banks.add(importBankFromJson(jsonPath));
            }

            if (!banks.isEmpty()) {
                amosFile = amosFile.withBanks(banks);
                System.out.printf("Attached %d bank(s)%n", banks.size());
            }

            System.out.println("Encoding...");
            var binary = tokenizer.encode(amosFile);
            System.out.println("Writing " + output);
            Files.write(output, binary);
            System.out.printf("Done (%d bytes)%n", binary.length);
            return 0;
        }
    }

    // =========================================================================
    // list
    // =========================================================================

    @Command(
            name = "list",
            mixinStandardHelpOptions = true,
            description = {
                    "Detokenize an AMOS binary file (.AMOS) to an ASCII source file (.Asc).",
                    "The output can be re-tokenized with the 'build' command."
            }
    )
    static class ListCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<input.AMOS>",
                description = "Binary AMOS file to detokenize")
        Path input;

        @Parameters(index = "1", paramLabel = "<output.Asc>",
                description = "Output ASCII source file")
        Path output;

        @Option(names = "--definition", paramLabel = "<path.json>",
                description = "Load an additional extension definition JSON file (repeatable).")
        List<Path> definitions = new ArrayList<>();

        @Override
        public Integer call() throws Exception {
            System.out.println("Reading " + input);
            var tokenizer = new Tokenizer();
            for (var defPath : definitions) {
                System.out.println("Loading definition " + defPath);
                tokenizer.withDefinition(defPath);
            }
            var amosFile = tokenizer.decode(input);
            System.out.printf("Detokenized %d lines (%s)%n",
                    amosFile.lines().size(), amosFile.version());
            System.out.println("Writing " + output);
            tokenizer.print(amosFile, output);
            System.out.printf("Done%n");
            return 0;
        }
    }

    // =========================================================================
    // disasm
    // =========================================================================

    @Command(
            name = "disasm",
            mixinStandardHelpOptions = true,
            description = "Disassemble an AMOS bank file (Abk) to JSON + data files in a directory."
    )
    static class DisasmCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<input.Abk>",
                description = "Input AMOS bank file (AmBk, AmSp, AmIc, …)")
        Path input;

        @Parameters(index = "1", paramLabel = "<output-dir>",
                description = "Output directory for JSON and data files")
        Path outDir;

        @Option(names = "--ilbm",
                description = "Export sprite/icon spritesheet as IFF ILBM instead of PNG")
        boolean ilbm = false;

        @Option(names = "--svx8",
                description = "Export samples as IFF 8SVX instead of RIFF WAVE")
        boolean svx8 = false;

        @Override
        public Integer call() throws Exception {
            System.out.printf("Reading %s ...%n", input.getFileName());
            var bank = AmosBank.read(input);
            Files.createDirectories(outDir);
            var stem = stem(input);
            System.out.printf("Bank type: %s%n", bank.type());

            switch (bank) {
                case SpriteBank sb -> new SpriteBankExporter().export(sb, outDir, ilbm);
                case ResourceBank rb -> new ResourceBankExporter().export(rb, outDir, ilbm);
                case AmalBank ab -> new AmalBankExporter().export(ab, outDir);
                case MenuBank mb -> new MenuBankExporter().export(mb, outDir);
                case MusicBank mb -> new MusicBankExporter().export(mb, outDir, svx8);
                case SampleBank sb -> new SampleBankExporter().export(sb, outDir, svx8);
                case TrackerBank tb -> new TrackerBankExporter().export(tb, outDir);
                case PacPicBank pb -> {
                    var ext = ilbm ? ".iff" : ".png";
                    new PacPicBankExporter().export(pb, outDir.resolve(stem + ext), ilbm);
                }
                case RawBank rb ->
                        new RawBankExporter().export(rb, outDir.resolve(stem + ".bin"));
                default -> {
                    System.err.println("Unsupported bank type: " + bank.type());
                    return 1;
                }
            }
            return 0;
        }
    }

    // =========================================================================
    // asm
    // =========================================================================

    @Command(
            name = "asm",
            mixinStandardHelpOptions = true,
            description = {
                    "Assemble a bank from JSON + data files into an Abk file.",
                    "The bank type is auto-detected from the 'type' field in the JSON."
            }
    )
    static class AsmCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<input.json>",
                description = "JSON metadata file; 'type' field determines the reader")
        Path json;

        @Parameters(index = "1", paramLabel = "<output.Abk>",
                description = "Output AMOS bank file")
        Path output;

        @Override
        public Integer call() throws Exception {
            System.out.printf("Importing bank from %s ...%n", json.getFileName());
            var bank = importBankFromJson(json);
            System.out.printf("Bank type: %s, writing %s ...%n", bank.type(), output.getFileName());
            bank.writer().write(bank, output);
            System.out.printf("Written %s%n", output);
            return 0;
        }
    }

    // =========================================================================
    // raw
    // =========================================================================

    @Command(
            name = "raw",
            mixinStandardHelpOptions = true,
            description = "Wrap a raw data file into an AMOS bank (Abk) with the given type."
    )
    static class RawCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<input>",
                description = "Raw data file to wrap")
        Path input;

        @Parameters(index = "1", paramLabel = "<output.Abk>",
                description = "Output AMOS bank file")
        Path output;

        @Option(names = "--type", required = true, paramLabel = "<TYPE>",
                description = "Bank type: WORK, DATA, MUSIC, SAMPLES, ASM, CODE, AMAL, MENU, TRACKER, DATAS")
        String type;

        @Option(names = "--chip",
                description = "Store in chip RAM (default: fast RAM)")
        boolean chip = false;

        @Option(names = "--bank-number", paramLabel = "<n>",
                description = "Bank slot number (default: ${DEFAULT-VALUE})")
        short bankNumber = 1;

        @Override
        public Integer call() throws Exception {
            System.out.printf("Reading %s ...%n", input.getFileName());
            var data = Files.readAllBytes(input);

            AmosBank.Type bankType;
            try {
                bankType = AmosBank.Type.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                System.err.println("Unknown bank type: " + type);
                System.err.println("Valid types: WORK, DATA, MUSIC, SAMPLES, ASM, CODE, AMAL, MENU, TRACKER, DATAS");
                return 1;
            }

            var bank = new RawBank(bankType, bankNumber, chip, data);
            bank.writer().write(bank, output);
            System.out.printf("Written %s  (%s, %s RAM, bank %d, %d bytes)%n",
                    output, bankType, chip ? "chip" : "fast", bankNumber & 0xFFFF, data.length);
            return 0;
        }
    }

    // =========================================================================
    // dev-help
    // =========================================================================

    @Command(
            name = "dev-help",
            description = "Show help for developer / diagnostic commands."
    )
    static class DevHelpCommand implements Callable<Integer> {

        @Override
        public Integer call() {
            System.out.println("""
                    Developer / diagnostic commands
                    ================================

                      portamos dump <file.AMOS>
                          Dump an AMOS binary as a human-readable token listing.

                      portamos diff <expected.AMOS> <actual.AMOS>
                          Diff two AMOS binary files at the token level.
                          Shows differing lines with EXP/ACT token pairs and context.

                      portamos gen-ext-json <input.Lib> --slot <n> [--start <s>] <output.json>
                          Generate a JSON definition skeleton from an AMOS extension binary.
                          --slot   Extension slot number (0=core, 1=Music, 2=Compact, …)
                          --start  Byte offset from token-table base to first entry
                                   (default: -194 for slot 0, 6 otherwise)
                    """);
            return 0;
        }
    }

    // =========================================================================
    // dump  (dev, hidden)
    // =========================================================================

    @Command(name = "dump", hidden = true,
            description = "Dump an AMOS binary as a human-readable token listing.")
    static class DumpCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<file.AMOS>",
                description = "AMOS binary file to dump")
        Path file;

        @Override
        public Integer call() throws Exception {
            new AmosDump().dump(file, System.out);
            return 0;
        }
    }

    // =========================================================================
    // diff  (dev, hidden)
    // =========================================================================

    @Command(name = "diff", hidden = true,
            description = "Diff two AMOS binary files at the token level.")
    static class DiffCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<expected.AMOS>",
                description = "Expected AMOS binary file")
        Path expected;

        @Parameters(index = "1", paramLabel = "<actual.AMOS>",
                description = "Actual AMOS binary file")
        Path actual;

        @Override
        public Integer call() throws Exception {
            new AmosDump().diff(expected, actual, System.out);
            return 0;
        }
    }

    // =========================================================================
    // gen-ext-json  (dev, hidden)
    // =========================================================================

    @Command(name = "gen-ext-json", hidden = true,
            description = "Generate a JSON definition skeleton from an AMOS extension binary.")
    static class GenExtJsonCommand implements Callable<Integer> {

        @Parameters(index = "0", paramLabel = "<input.Lib>",
                description = "Extension binary (.Lib file)")
        Path input;

        @Parameters(index = "1", paramLabel = "<output.json>",
                description = "Output JSON skeleton file")
        Path output;

        @Option(names = "--slot", required = true, paramLabel = "<n>",
                description = "Extension slot number")
        int slot;

        @Option(names = "--start", paramLabel = "<s>",
                description = "Byte offset from token-table base to first entry "
                        + "(default: -194 for slot 0, 6 otherwise)")
        Integer start;

        @Override
        public Integer call() throws Exception {
            var resolvedStart = (start != null) ? start : (slot == 0 ? -194 : 6);
            System.out.printf("Generating JSON from %s (slot=%d, start=%d)%n",
                    input.getFileName(), slot, resolvedStart);
            ExtJsonGenerator.generate(input, slot, resolvedStart, output);
            return 0;
        }
    }

    // =========================================================================
    // Shared helpers
    // =========================================================================

    /** Auto-detects bank type from the {@code "type"} field in the JSON and imports the bank. */
    static AmosBank importBankFromJson(Path jsonPath) throws IOException {
        var mapper = new ObjectMapper();
        var root = mapper.readTree(jsonPath.toFile());
        var type = root.path("type").asText("");
        return switch (type.toLowerCase()) {
            case "resource" -> new ResourceBankImporter().importFrom(jsonPath);
            case "sprite", "sprites" -> new SpriteBankImporter().importFrom(jsonPath);
            case "icon", "icons" -> new SpriteBankImporter().importFrom(jsonPath);
            case "pacpic" -> new PacPicBankImporter().importFrom(jsonPath);
            case "work", "data" -> new RawBankImporter().importFrom(jsonPath);
            case "amal" -> new AmalBankImporter().importFrom(jsonPath);
            case "menu" -> new MenuBankImporter().importFrom(jsonPath);
            case "samples" -> new SampleBankImporter().importFrom(jsonPath);
            case "tracker" -> new TrackerBankImporter().importFrom(jsonPath);
            case "music" -> new MusicBankImporter().importFrom(jsonPath);
            default -> throw new IllegalArgumentException(
                    "Unknown bank type in JSON: \"" + type + "\". "
                            + "Expected: resource, sprite, icon, pacpic, work, data");
        };
    }

    /** Returns the filename stem (everything before the last dot). */
    static String stem(Path path) {
        var name = path.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }
}
