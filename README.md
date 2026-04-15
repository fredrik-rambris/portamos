# Portamos

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A portable, JVM-based toolkit for working
with [AMOS Professional](https://en.wikipedia.org/wiki/AMOS_(programming_language)) files on modern systems. The name is
a play on *portable AMOS*.

AMOS Professional is a BASIC-like programming language for the Commodore Amiga, developed by François Lionet. Programs
are stored as tokenised binary files (`.AMOS`), and multimedia data is stored in memory bank files (`.Abk`).

## Features

| Feature                                           | Status         |
|---------------------------------------------------|----------------|
| ASCII source (`.Asc`) → binary (`.AMOS`)          | ✅ Working      |
| Binary (`.AMOS`) → ASCII source (`.Asc`)          | 🔲 Not started |
| Banks embedded in `.AMOS` files                   | ✅ Working      |
| Resource bank read/write/export/import            | ✅ Working      |
| Sprite / Icon bank read/write/export/import       | ✅ Working      |
| Pac.Pic bank read/write/export/import             | ✅ Working      |
| Work / Data bank read/write/export/import         | ✅ Working      |
| AMAL bank read/write/export/import                | ✅ Working      |
| Menu bank read/write/export/import                | ✅ Working      |
| Sample bank read/write/export/import (WAV / 8SVX) | ✅ Working      |
| Music bank read/write/export/import (WAV / 8SVX)  | ✅ Working      |
| Tracker bank read/write/export/import             | ✅ Working      |

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
./gradlew run --args="<subcommand> [options]"
```

## CLI Reference

Run `portamos help` or `portamos <subcommand> --help` for full option listings.

### `build` — tokenize an ASCII source file

```bash
portamos build source.Asc output.AMOS
portamos build source.Asc output.AMOS --add-bank sprites.Abk --add-bank music.Abk
portamos build source.Asc output.AMOS --import-bank sprites/bank.json
portamos build source.Asc output.AMOS --definition definitions/turboplus.json
portamos build source.Asc output.AMOS --fold
```

Reads an AMOS Professional ASCII source file and writes the corresponding binary `.AMOS` file.
Optionally attaches bank files directly (`--add-bank`) or assembles them from JSON (`--import-bank`).

**`--definition <path.json>`** — Load an additional extension definition file before tokenizing
(repeatable). Use this for third-party extensions not included in the built-in set. The release
archives include a `definitions/` directory with definitions for known third-party extensions.

**`--fold`** — Mark all `Procedure` blocks as folded in the AMOS editor by default (bit 7 of the
procedure flags byte). Without this flag, procedures are saved in the unfolded state.

### `disasm` — export a bank to files

```bash
portamos disasm input.Abk output-dir/
portamos disasm --ilbm input.Abk output-dir/    # sprite sheet as IFF ILBM instead of PNG
portamos disasm --svx8 input.Abk output-dir/    # samples as IFF 8SVX instead of WAV
```

Exports the contents of any supported bank type to a directory. The output always includes a
`bank.json` metadata file plus type-specific data files:

| Bank type   | Data files                                        |
|-------------|---------------------------------------------------|
| Resource    | `spritesheet.png` (or `.iff`), `program_NNN.amui` |
| Sprite/Icon | `spritesheet.png` (or `.iff`)                     |
| Pac.Pic     | `<name>.png` (or `.iff`)                          |
| Music       | `instrument_NNN.wav` (or `.8svx`)                 |
| Sample      | `sample_NNN.wav` (or `.8svx`)                     |
| Tracker     | `<name>.mod`                                      |
| AMAL        | `script_NNN.amal`                                 |
| Work/Data   | `<name>.bin`                                      |

### `asm` — assemble a bank from files

```bash
portamos asm bank.json output.Abk
```

Reads the `bank.json` produced by `disasm` and reassembles the binary `.Abk` file. The `"type"`
field in the JSON determines which importer is used.

### `raw` — wrap a raw file into a bank

```bash
portamos raw payload.bin output.Abk --type WORK
portamos raw payload.bin output.Abk --type MUSIC --chip --bank-number 3
```

Wraps raw bytes in an AmBk envelope with the specified type. Valid types: `WORK`, `DATA`, `MUSIC`,
`SAMPLES`, `ASM`, `CODE`, `AMAL`, `MENU`, `TRACKER`, `DATAS`.

### Developer commands

These are hidden in `--help` but documented in `portamos dev-help`:

```bash
portamos dump file.AMOS                         # token-level dump
portamos diff expected.AMOS actual.AMOS         # token-level diff
portamos gen-ext-json input.Lib --slot N output.json  # generate JSON from .Lib
```

Always use `dump` / `diff` instead of `xxd` or binary diff tools — they correctly handle
variable-length token payloads and show decoded annotations.

## Bank JSON Format

All bank JSON files have a `"type"` field that identifies the bank and drives `asm` import.

### Music bank

```json
{
  "type": "Music",
  "bankNumber": 3,
  "chipRam": true,
  "instruments": [
    {
      "name": "Bass",
      "volume": 45,
      "loopStart": 112,
      "loopLength": 3247,
      "sample": "instrument_000.wav"
    }
  ],
  "songs": [
    {
      "name": "My Song",
      "tempo": 15,
      "sequence": [
        [
          0,
          1,
          2,
          3,
          65534
        ],
        [
          0,
          1,
          2,
          3,
          65534
        ],
        [
          0,
          1,
          2,
          3,
          65534
        ],
        [
          0,
          1,
          2,
          3,
          65534
        ]
      ]
    }
  ],
  "patterns": [
    {
      "voices": [
        [
          {
            "command": "SET_INSTR",
            "parameter": 0
          },
          {
            "period": 254,
            "duration": 16134
          },
          {
            "period": 190,
            "duration": 16134
          }
        ],
        ...
      ]
    }
  ]
}
```

`songs[].sequence` — four per-channel playlists; each is a list of 0-based pattern indices
terminated by `65534` (loop) or `65535` (stop). The four channels run independently — unlike
tracker/MOD format there is no shared row grid.

`patterns[].voices` — four independent note streams. Each item is either a note
(`period` + `duration`) or a command (`command` name + optional `parameter`). The `duration` is
the raw OldNote control word (bit 14 cleared); bits 13–8 encode per-note channel volume (0–63),
bits 7–0 encode the tick count. `period` is the Amiga hardware period (inversely proportional to
frequency; 0 = silence).

Known command names: `SET_VOLUME`, `STOP_EFFECT`, `REPEAT`, `FILTER_ON`, `FILTER_OFF`,
`SET_TEMPO`, `SET_INSTR`, `ARPEGGIO`, `TONE_PORTAMENTO`, `VIBRATO`, `VOLUME_SLIDE`,
`PORTAMENTO_UP`, `PORTAMENTO_DOWN`, `DELAY`, `POSITION_JUMP`.

Instrument samples are exported at 8363 Hz (Amiga standard tuning); actual playback pitch is
determined by the note periods at runtime.

### Sample bank

```json
{
  "type": "Samples",
  "bankNumber": 1,
  "chipRam": true,
  "samples": [
    {
      "index": 0,
      "name": "BanjoSyn",
      "frequencyHz": 8363,
      "file": "sample_000.wav"
    },
    {
      "index": 1,
      "name": "Empty",
      "frequencyHz": 8363,
      "empty": true
    }
  ]
}
```

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

```
[4]   magic: "AmBk" / "AmSp" / "AmIc"
[2]   bank number
[2]   flags (bit 0: 0 = chip RAM, 1 = fast RAM)
[4]   name+payload size (bit 31 set for chip-RAM AmBk banks)
[8]   bank name (space-padded): "Music   ", "Samples ", "Resource", …
[n]   payload (format depends on bank type)
```

## Project Structure

```
src/main/java/dev/rambris/amigaamos/
  Main.java                           CLI entry point (subcommands: build/disasm/asm/raw/…)
  tokenizer/
    Tokenizer.java                    Public API: parse() + encode()
    AsciiParser.java                  ASCII source line → token list
    BinaryEncoder.java                Token list → binary bytes
    AmosFileWriter.java               Lines + banks → .AMOS file
    TokenTable.java                   JSON definitions → token lookup
    AmosDump.java                     Token dump and diff tool
    ExtJsonGenerator.java             .Lib binary → JSON skeleton
    model/
      AmosFile.java, AmosLine.java, AmosToken.java, AmosVersion.java
  bank/
    AmosBank.java                     Interface + type dispatch
    BankWriter.java                   Interface: write(bank, path) / toBytes(bank)
    AmBkCodec.java                    Shared AmBk header encode/decode
    ResourceBank{Reader,Writer,Exporter,Importer}.java
    SpriteBank{Reader,Writer,Exporter,Importer}.java
    PacPicBank{Reader,Writer,Exporter,Importer}.java
    MusicBank{Reader,Writer,Exporter,Importer}.java   + MusicBank.java (model + Command enum)
    SampleBank{Reader,Writer,Exporter,Importer}.java
    TrackerBank{Reader,Writer,Exporter,Importer}.java
    AmalBank{Reader,Writer,Exporter,Importer}.java
    MenuBank{Reader,Writer,Exporter,Importer}.java
    RawBank{Reader,Writer,Exporter,Importer}.java

src/main/resources/amos/definitions/
  core.json, music.json, compact.json, request.json, ioports.json

definitions/                            Third-party extension definitions (shipped in release archives)
  turboplus.json                        TURBO Plus extension (slot 12, Manuel Andre)

reference/
  AMOSProfessional/                   Original AMOS Pro disk image and .Lib files
  AmosProManual/                      AMOS Pro manual
  amiga-amos/                         Source JSON token definitions (IntelliJ plugin)
  amostools/extensions/               Third-party .Lib files
  amos-file-formats.wiki              Exotica file format docs
  amos-music-bank-format.wiki         Exotica Music bank format docs
```

## Reference Material

| Path                                    | Description                                                                                   |
|-----------------------------------------|-----------------------------------------------------------------------------------------------|
| `reference/AMOSProfessional/`           | Full AMOS Pro disk image — original `.Lib` files, examples, accessories                       |
| `reference/AmosProManual/`              | [AMOS Pro manual](https://amospromanual.dev)                                                  |
| `reference/amiga-amos/`                 | JSON token definitions from [intellij-amos](https://github.com/fredrik-rambris/intellij-amos) |
| `reference/amostools/extensions/`       | Third-party `.Lib` extension files                                                            |
| `reference/amos-file-formats.wiki`      | File format docs from [Exotica](https://www.exotica.org.uk/wiki/AMOS_file_formats)            |
| `reference/amos-music-bank-format.wiki` | Music bank format docs from Exotica                                                           |

## Scripts

```bash
# Re-enrich JSON definitions from binary .bin tables
python3 scripts/enrich_definitions.py

# Re-generate per-signature offset schema after enrichment
python3 scripts/migrate_offsets.py
```

## Known Limitations

- **Detokenizer** (binary → ASCII) is not yet implemented
- Symbol-table slot offsets (`unk2` in named tokens) are written as zero; AMOS recomputes them
  at load time so programs run correctly, but the binary is not byte-identical to AMOS-produced files
- ~66 core keyword definitions lack binary offsets (aliases and optional-suffix variants) and are
  silently skipped during tokenisation
- Third-party extensions beyond the five bundled JSON files require running `gen-ext-json` to
  generate definition skeletons
