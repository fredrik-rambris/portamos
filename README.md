# Portamos

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A portable, JVM-based toolkit for working
with [AMOS Professional](https://en.wikipedia.org/wiki/AMOS_(programming_language)) files on modern systems. The name is
a play on *portable AMOS*.

AMOS Professional is a BASIC-like programming language for the Commodore Amiga, developed by François Lionet. Programs
are stored as tokenised binary files (`.AMOS`), and multimedia data is stored in memory bank files (`.Abk`).

## Features

| Feature                                  | Status         |
|------------------------------------------|----------------|
| ASCII source (`.Asc`) → binary (`.AMOS`) | ✅ Working      |
| Binary (`.AMOS`) → ASCII source (`.Asc`) | 🔲 Not started |
| Resource bank read/write (`.Abk`)        | ✅ Working      |
| Resource bank export/import (PNG + JSON) | ✅ Working      |
| Work/Data bank read/write (`.Abk`)       | ✅ Working      |
| Work/Data bank export/import             | ✅ Working      |
| Banks embedded in `.AMOS` files          | ✅ Working      |

## Requirements

- Java 21 (tested with [Eclipse Temurin 21](https://adoptium.net/))

The Gradle wrapper (`./gradlew`) downloads Gradle automatically; no separate installation is needed.

## Building

```bash
./gradlew build
```

This produces a fat JAR at `build/libs/portamos-<version>-all.jar`.

You can also run directly via Gradle:

```bash
./gradlew run --args="<arguments>"
```

## CLI Reference

### Tokenize an ASCII source file to binary

```bash
portamos source.Asc output.AMOS
```

Reads an AMOS Professional ASCII source file and writes the corresponding binary `.AMOS` file.

### Dump a binary file as human-readable tokens

```bash
portamos --dump file.AMOS
```

Prints a token-by-token listing of a `.AMOS` binary file. More useful than `xxd` because it correctly handles
variable-length token payloads (strings, variable names, REMs, etc.) and shows decoded annotations alongside the raw
hex.

Mainly used for debugging the tokenizer

### Diff two binary files at the token level

```bash
portamos --diff expected.AMOS actual.AMOS
```

Compares two `.AMOS` binary files and reports every line that differs, with decoded token annotations. Use this instead
of a raw binary diff when debugging tokenizer output.

### Disassemble a Resource bank

```bash
portamos --disasm-bank input.Abk output-dir/
```

Exports the contents of a Resource bank (`.Abk`) to a directory:

| File               | Contents                                                                          |
|--------------------|-----------------------------------------------------------------------------------|
| `<name>.png`       | Indexed-colour sprite sheet (all images composited at their original coordinates) |
| `program_NNN.amui` | DBL Interface programs (UTF-8 text)                                               |
| `bank.json`        | Bank metadata: palette, screen mode, element list, texts                          |

### Assemble a Resource bank from a directory

```bash
portamos --asm-bank input-dir/ output.Abk
```

Reads a `bank.json` and the associated sprite sheet and program files, and writes a `.Abk` file. The inverse of
`--disasm-bank`.

### Generate a JSON definition skeleton from an extension binary

```bash
portamos --gen-ext-json AMOSPro_MyExt.Lib --slot 4 output.json
```

Generates a JSON skeleton for an AMOS extension `.Lib` file. Useful when adding support for a new third-party extension.

Options:

- `--slot N` — extension slot number (required)
- `--start S` — byte offset from the token-table base to the first entry (default: `-194` for slot 0, `6` for all
  others)

## File Formats

### `.AMOS` — Program binary

```
[16]  version header (e.g. "AMOS Pro101v\0\0\0\0")
[4]   code length in bytes (big-endian uint32)
[n]   tokenised code — sequence of lines, each:
        [1]  line length in 16-bit words
        [1]  indent level (1-based)
        [?]  token stream
        [2]  0x0000 end-of-line marker
[6+]  "AmBs" + uint16 bank count + optional bank data
```

### `.Abk` — Memory bank

All bank files share a common 12-byte header:

```
[4]   "AmBk"
[2]   bank number
[2]   flags (0x0000 = chip RAM, 0x0001 = fast RAM)
[4]   name + payload size
[8]   bank name (space-padded): "Resource", "Work    ", "Data    ", …
```

#### Resource bank

Follows the header with a sub-header containing offsets to three sections: images (Pac.Pic compressed sprites with
palette), texts, and DBL Interface programs.

#### Work / Data bank

Follows the header immediately with raw payload bytes. Work and Data banks are structurally identical; the bank name in
the header distinguishes them.

## Bank Export / Import

Resource, Work and Data banks can be exported to files and imported back, making it possible to edit assets outside
AMOS.

### Resource bank

```bash
# Export
portamos --disasm-bank MySprites.Abk sprites/

# Import
portamos --asm-bank sprites/ MySprites.Abk
```

The sprite sheet is a standard indexed-colour PNG. Edit it with any image editor that preserves the palette and colour
indices (e.g. [Aseprite](https://www.aseprite.org/), [GIMP](https://www.gimp.org/) in indexed mode).

### Work / Data bank

Export via the Java API (`RawBankExporter`) or build one programmatically:

```java
// Read
AmosBank bank = new RawBankReader().read(Path.of("MyData.Abk"));

// Build
RawBank work = RawBank.Work(data);      // WORK bank, fast RAM
RawBank chipWork = RawBank.ChipWork(data);  // WORK bank, chip RAM
RawBank data_ = RawBank.Data(data);      // DATA bank, fast RAM
RawBank chipData = RawBank.ChipData(data);  // DATA bank, chip RAM

// Export: writes MyData.bin and MyData.bin.json
new

RawBankExporter().

export(bank, Path.of("MyData.bin"));

// Import: reads JSON, resolves data file relative to the JSON
AmosBank imported = new RawBankImporter().importFrom(Path.of("MyData.bin.json"));
```

The sidecar `.json` file stores `type`, `bankNumber`, `chipRam`, and `dataFile` (filename only). When importing you can
point `dataFile` at a renamed or replaced file; the importer resolves it relative to the JSON file's location.

## Java API

The main entry points:

```java
// Tokenize an ASCII source file
Tokenizer tokenizer = new Tokenizer();              // defaults to PRO_101
AmosFile program = tokenizer.parse(path);
byte[] binary = tokenizer.encode(program);

// Attach banks before encoding
program =program.

withBanks(List.of(bank1, bank2));
binary  =tokenizer.

encode(program);               // banks written after AmBs

// Read a Resource bank
ResourceBank bank = new ResourceBankReader().read(Path.of("MyBank.Abk"));
```

If a parse error occurs, a `TokenizeException` is thrown with the 1-based line number, column (when known), and the
source text of the offending line.

## Project Structure

```
src/main/java/dev/rambris/amos/
  Main.java                         CLI entry point
  tokenizer/
    Tokenizer.java                  Public API: parse() + encode()
    AsciiParser.java                ASCII source line → token list
    BinaryEncoder.java              Token list → binary line bytes
    AmosFileWriter.java             Binary lines + banks → .AMOS file
    TokenTable.java                 JSON definitions → token lookup
    TokenizeException.java          Parse error with line/column context
    AmosDump.java                   Token dump and diff tool
    ExtJsonGenerator.java           .Lib binary → JSON skeleton
    model/
      AmosFile.java                 Program: version + lines + banks
      AmosLine.java                 One source line: indent + tokens
      AmosToken.java                Sealed interface, all token variants
      AmosVersion.java              PRO_101 / BASIC_134 / BASIC_13
  bank/
    AmosBank.java                   Interface for all bank types
    BankWriter.java                 Interface: write / toBytes
    ResourceBank.java               Resource bank model + builder
    ResourceBankReader.java         .Abk reader for Resource banks
    ResourceBankWriter.java         .Abk writer for Resource banks
    ResourceBankExporter.java       Resource bank → PNG + JSON
    ResourceBankImporter.java       PNG + JSON → Resource bank
    RawBank.java                    Work / Data bank model + factories
    RawBankReader.java              .Abk reader for Work/Data banks
    RawBankWriter.java              .Abk writer for Work/Data banks
    RawBankExporter.java            Work/Data bank → data file + JSON
    RawBankImporter.java            JSON → Work/Data bank
    PacPicEncoder.java              Chunky pixels → Pac.Pic compression
    PacPicDecoder.java              Pac.Pic → chunky pixels

src/main/resources/amos/definitions/
  core.json                         Core AMOS keywords
  music.json                        Music extension (slot 1)
  compact.json                      Compact extension (slot 2)
  request.json                      Request extension (slot 3)
  ioports.json                      IOPorts extension (slot 6)
```

## Reference Material

The `reference/` directory (not included in the repository) contains:

| Dir                    | Description                                                                                                   |
|------------------------|---------------------------------------------------------------------------------------------------------------|
| AMOSProfessional/      | Full AMOS Pro disk image + source                                                                             |
| AmosProManual/         | [AMOS Pro manual from](https://amospromanual.dev)                                                             |
| amiga-amos/            | Original JSON token definitions from [AMOS IntelliJ plugin](https://github.com/fredrik-rambris/intellij-amos) |
| amostools/extensions/  | Third-party .Lib extension files                                                                              |
| amos-file-formats.wiki | File format documentation from [Exotica](https://www.exotica.org.uk/wiki/AMOS_file_formats)                   |

## Scripts

```bash
# Re-enrich JSON definitions from binary .bin tables
python3 scripts/enrich_definitions.py

# Re-generate per-signature offset schema after enrichment
python3 scripts/migrate_offsets.py
```

## Known Limitations

- **Detokenizer** (binary → ASCII) is not yet implemented
- Symbol-table slot offsets (`unk2` in named tokens) are written as zero; AMOS recomputes them at load time, so programs
  still run correctly
- ~66 core definitions lack binary offsets (aliases and optional-suffix variants); they are silently skipped during
  tokenisation
- Third-party extensions beyond the five bundled JSON files require running `--gen-ext-json` to generate definition
  skeletons
