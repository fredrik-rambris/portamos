# Portamos — Project Context for Claude

## What this project is

**Portamos** is a portable, JVM-based re-implementation of the AMOS Professional compiler/tokenizer written in Java 21 /
Gradle 8. The name is a play on "portable AMOS".

The long-term goal is a full round-trip pipeline:

- **ASCII → AmosFile → binary** (tokenizer/compiler, currently working)
- **binary → AmosFile → ASCII** (detokenizer, not yet started)

The immediate milestone is the tokenizer: given an AMOS Professional ASCII source file (`.Asc`), produce a
byte-identical (or structurally equivalent) binary `.AMOS` file that AMOS Pro can load on an Amiga.

## Reference material

| Path                              | What it is                                                                                                                                                       |
|-----------------------------------|------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `reference/AMOSProfessional/`     | Full AMOS Pro disk image — original `.Lib` files, examples, accessories                                                                                          |
| `reference/AmosProManual/`        | The AMOS Pro manual (source for the JSON definition files)                                                                                                       |
| `reference/amiga-amos/`           | A separate IntelliJ plugin project; its `src/main/resources/amos/definitions/*.json` are the canonical definition files we enriched and copied into this project |
| `reference/amostools/extensions/` | Pre-decoded binary token tables: `00base.bin`, `01_music.h`, `02_compact.h`, etc.; also a large collection of third-party `.Lib` files                           |

## Environment

- Java 21 (Temurin), pinned via `.sdkmanrc` (`java=21.0.6-tem`)
- `gradle.properties` is **gitignored** — it must set `org.gradle.java.home` to a Java 21 path on each machine
- Gradle Kotlin DSL (`build.gradle.kts`)
- Jackson Databind 2.18.3 for JSON parsing
- JUnit Jupiter 5.11.4 for tests

## Project structure

```
src/main/java/dev/rambris/amos/
  Main.java                          CLI entry point
  tokenizer/
    Tokenizer.java                   Public API: parse() + encode()
    AsciiParser.java                 ASCII source line → List<AmosToken>
    BinaryEncoder.java               List<AmosToken> → binary line bytes
    AmosFileWriter.java              List<line-bytes> → complete .AMOS file
    TokenTable.java                  JSON definitions → name→key lookup table
    ExtJsonGenerator.java            .Lib binary → JSON skeleton generator
    AmosDump.java                    Token-level dump and diff tool (use instead of xxd/diff)
    model/
      AmosFile.java                  In-memory program: version + List<AmosLine>
      AmosLine.java                  record(int indent, List<AmosToken> tokens)
      AmosToken.java                 Sealed interface with all token variants
      AmosVersion.java               enum: PRO_101 / BASIC_134 / BASIC_13

src/main/resources/amos/
  definitions/                       JSON token definitions (enriched with offsets)
    core.json                        Core AMOS keywords (slot 0, start -194)
    music.json                       Music extension (slot 1, start 6)
    compact.json                     Compact extension (slot 2, start 6)
    request.json                     Request extension (slot 3, start 6)
    ioports.json                     IOPorts extension (slot 6, start 6)

src/test/resources/
  Numbers.Asc / Numbers.AMOS           Integration test pair (BASIC_13)
  PaletteEditor.Asc / PaletteEditor.AMOS  Integration test pair (PRO_101)
  Procedures_2.Asc / Procedures_2.AMOS Integration test pair (BASIC_134)

scripts/
  enrich_definitions.py              One-time script: binary → JSON offset enrichment
  migrate_offsets.py                 Migrates JSON definitions to per-signature offset schema
```

## Core data model

```
AmosFile
  AmosVersion version
  List<AmosLine> lines

AmosLine
  int indent        1-based; level 1 = no indentation (0 spaces)
  List<AmosToken> tokens

AmosToken (sealed interface variants):
  SingleQuoteRem(text)      token $0652 — ' whole-line comment
  Rem(text)                 token $064A — Rem keyword comment
  DoubleQuoteString(text)   token $0026
  SingleQuoteString(text)   token $002E
  DecimalInt(value)         token $003E + 4-byte int
  HexInt(value)             token $0036 + 4-byte int
  BinaryInt(value)          token $001E + 4-byte int
  Flt(value)                token $0046 + 4-byte AMOS float
  Dbl(value)                token $2B6A + 8-byte IEEE double
  Variable(name,type,isArray,extraFlags)  token $0006
  Label(name)               token $000C — label definition (e.g. "SHOERR:")
  ProcRef(name)             token $0012 — procedure call
  LabelRef(name)            token $0018 — Goto/Gosub target
  Keyword(value)            raw 2-byte core keyword token
  ExtKeyword(slot,offset)   token $004E + slot + $00 + offset
```

## AMOS binary file format

```
[16 bytes]  version header (e.g. "AMOS Basic v1.3 " or "AMOS Pro101v\0\0\0\0")
[4 bytes]   uint32 BE code length in bytes
[n bytes]   code section: sequence of encoded lines
[6 bytes]   "AmBs" + uint16 bank count (0x0000 for no banks)
```

Each line in the code section:

```
[1 byte]    line length in 16-bit words (covers the whole line incl. header + EOL)
[1 byte]    indent level (1-based)
[n bytes]   token stream
[2 bytes]   0x0000 end-of-line
```

## Token encoding rules

| Token                                         | Encoding                                                                                                                                        |
|-----------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------|
| Named token (Variable/Label/ProcRef/LabelRef) | `[type:2][unk1:00][unk2:00][n:1][flags:1][name bytes][pad if odd]` — unk2 is a symbol-table slot offset filled by AMOS at load time; we write 0 |
| Rem / SingleQuoteRem                          | `[type:2][00][len:1][text bytes][pad if odd]`                                                                                                   |
| Quoted string                                 | `[type:2][len:2 BE][text bytes][pad if odd]`                                                                                                    |
| Integer                                       | `[type:2][value:4 BE signed]`                                                                                                                   |
| Float (Flt)                                   | `[0046][AMOS FFP value:4 BE]`                                                                                                                   |
| Double (Dbl)                                  | `[2B6A][IEEE 754 double:8 BE]`                                                                                                                  |
| Core keyword                                  | `[offset:2 BE]` (+ extra zero bytes for control-flow tokens)                                                                                    |
| Extension keyword                             | `[004E][slot:1][00][offset:2 BE]`                                                                                                               |

## AMOS custom float (FFP)

- 32 bits: bits 31-8 = mantissa (24-bit, MSB always set for non-zero); bits 6-0 = exponent; bit 7 unused
- `value = mantissa × 2^(exponent − 88)`
- Negatives are always encoded as `unary-minus-operator + positive-literal` — the Flt token itself is always positive
- Amiga FFP rounding differs from IEEE 754; tests use ±4 ULP tolerance (observed max difference ~2.7 ULP for edge cases
  near the max FFP float)

## Token table / JSON definitions

`TokenTable.java` loads the JSON files and builds:

- `nameToSignatures`: normalized-uppercase name → `List<SignatureEntry(key, commaGroups)>` sorted ascending by
  `commaGroups`
- `keyToExtraBytes`: encoding key → count of zero bytes to write after the token

**Slot 0 (core)** keys are used directly as `uint16` in the binary.
**Slot N** keys are encoded as `ExtKeyword(slot, offset)` → `0x004E slot 0x00 offset`.

**Core operators** are NOT in the JSON files; they are hardcoded in `TokenTable.CORE_OPERATORS`:
`XOR, <>, ><, <=, =<, >=, =>, =, <, >, +, -, MOD, *, /, ^, :, ,, ;, #`

**`extraBytes`** (zero bytes after the token for runtime back-patch space):

- For/Repeat/While/Do/If/Else/Data/Else If → 2 bytes
- Exit/Exit If/On → 4 bytes
- Procedure → 8 bytes
- Equ/Lvo/Struc/Struc$ → 6 bytes

## JSON definition file schema

```json
{
  "extension": {
    "id": "Core",
    // or "Music", "Compact", etc.
    "slot": 0,
    // extension slot number
    "start": -194,
    // byte offset from tkoff to first entry (-194 for core, 6 for extensions)
    "filename": "...",
    // original .Lib filename
    "vendor": "..."
  },
  "definitions": [
    {
      "name": "FOR",
      // uppercase
      "kind": "structure",
      // instruction | function | structure | operator
      "offset": 572,
      // decimal byte offset in the token table (required for tokenization)
      "extraBytes": 2,
      // optional; zero bytes after token value
      "documentation": "...",
      "signatures": [
        ...
      ],
      "link": "..."
    }
  ]
}
```

Definitions **without** an `"offset"` field are documentation-only and are skipped by the tokenizer. This applies to ~66
entries in core.json (aliases, optional-suffix forms like "INVERSE ON/OFF", etc.).

**Multiple signatures / multi-form keywords**: Many AMOS keywords have 2–4 binary table entries at different offsets,
one per argument-count variant. Each signature in the JSON has its own `"offset"` field. `TokenTable` builds a list of
`SignatureEntry(key, commaGroups)` per keyword, sorted ascending by `commaGroups`. `AsciiParser.countCommaGroups()`
counts the top-level comma groups following the keyword on the source line (handling parenthesized function-call
syntax), and `TokenTable.selectKey()` picks the highest `commaGroups ≤ actual` — falling back to the first signature if
none qualify.

Keywords whose form ordering is non-obvious (e.g. `X Screen(x)` vs `X Screen(screen,x)`, `Paint x,y` vs
`Paint x,y,colour`) are listed in `OVERRIDES` in `scripts/migrate_offsets.py`. When a new test reveals a wrong form, add
the keyword there and re-run the migration script.

## AsciiParser internals

Key behaviors to know:

- **Indent**: 1 space = 1 indent level (i.e. `indent = leadingSpaces + 1`). Note: `AMOS_INDENT_SPACES = 1`.
- **Rem handling**: `'` at line start → `SingleQuoteRem`; `Rem ` at line start → `Rem`; `Rem` mid-line → `Rem` and stop
  parsing.
- **Numeric literals**: never emits negative literals; a leading `-` is an operator token.
- **Scientific notation**: AMOS stores floats with a space before the exponent: `"9.22337 E+18"` — parser handles this.
- **Keyword matching**: maximal munch up to 3 words; tries longest match first.
- **Pre-scan**: `Tokenizer` pre-scans all lines before parsing to collect `procedureNames` and `arrayVarNames` — used by
  `AsciiParser` for context-sensitive identifier classification.
- **Variable flags**: INTEGER=0x00, FLOAT=0x01, STRING=0x02; array flag=0x40.
- **Named token encoding**: n = `strlen` rounded up to even; for odd-length names, the padding byte is the null
  terminator. Even-length names have no null.

## CLI usage

```bash
# Tokenize ASCII source to binary
./gradlew run --args="source.Asc output.AMOS"

# Dump an AMOS binary as a human-readable token listing
./gradlew run --args="--dump file.AMOS"

# Diff two AMOS binary files at the token level — use this instead of xxd/diff
# Shows each differing line with EXP/ACT token pairs; matching tokens shown for context
./gradlew run --args="--diff expected.AMOS actual.AMOS"

# Generate a JSON skeleton from any .Lib binary
./gradlew run --args="--gen-ext-json AMOSPro_3d.Lib --slot 4 output.json"
# Default start: -194 for slot 0, 6 for all other slots
```

## Debugging tokenizer differences

**Always use `--diff` / `--dump` instead of `xxd`, `sed`, or raw binary diff tools.**

`AmosDump` walks the token stream correctly (respecting variable-length payloads for named tokens, strings, REMs, etc.)
so reported byte offsets are token-aligned and meaningful.

Typical workflow when a test fails with `Line N offset M: token value differs (exp=0xXXXX, act=0xYYYY)`:

1. Tokenize the `.Asc` file to a temp file:
   ```bash
   ./gradlew run --args="source.Asc /tmp/actual.AMOS"
   ```
2. Diff against the reference:
   ```bash
   ./gradlew run --args="--diff src/test/resources/file.AMOS /tmp/actual.AMOS"
   ```
3. The diff output shows the exact token where the encoder diverges, with decoded annotations (variable names, string
   contents, keyword offsets) alongside the raw hex — enough to identify which keyword form was selected incorrectly.

When diagnosing a wrong keyword form, look up the two candidate offsets in
`src/main/resources/amos/definitions/core.json` (or the relevant extension JSON) to understand their `commaGroups`
values, then check whether `countCommaGroups()` in `AsciiParser` is computing the right count for that source line.

## scripts/enrich_definitions.py

Run to re-enrich the JSON definition files whenever the binary `.bin` files change or new offset data is needed:

```bash
cd portamos
python3 scripts/enrich_definitions.py
# Then copy updated JSONs: cp reference/amiga-amos/src/main/resources/amos/definitions/*.json \
#   src/main/resources/amos/definitions/
```

The script matches binary entries to JSON definitions by normalized uppercase name. Unmatched binary entries (operators,
internal tokens) are reported but not written. JSON entries without a binary match get no `offset` and are silently
skipped at tokenization time.

## scripts/migrate_offsets.py

Run after `enrich_definitions.py` whenever the reference JSON definitions change, to produce the per-signature-offset
JSON consumed by the tokenizer:

```bash
cd portamos
python3 scripts/migrate_offsets.py
```

Reads from `reference/amiga-amos/src/main/resources/amos/definitions/` and writes to
`src/main/resources/amos/definitions/`. The `OVERRIDES` dict in the script contains manual form-ordering for keywords
whose argument-count mapping cannot be auto-derived (e.g. `MID$`, `SCREEN`, `PAINT`, `X SCREEN`, `Y SCREEN`). Add new
entries there when tests reveal incorrect form selection.

## Known gaps / future work

- **Detokenizer** (binary → AmosFile → ASCII): not started
- **Bank data**: `AmosFile` is designed to hold bank data (graphics, samples) alongside lines, but banks are not yet
  parsed or written — `AmosFileWriter` always writes zero banks
- **JSON coverage gaps**: ~66 core definitions lack offsets (documented aliases, optional-suffix variants); 3 compact
  entries (GET CBLOCK, PUT CBLOCK, DEL CBLOCK) have no binary counterpart; music "TRACK LOOP OFF" in JSON is spelled "
  TRACK LOOP OF" in the binary
- **Third-party extensions**: `reference/amostools/extensions/` contains ~80 third-party `.Lib` files; use
  `--gen-ext-json` to generate JSON skeletons for them
- **Symbol table offsets** (`unk2` in named tokens): AMOS fills in a slot offset (6 bytes per variable) into the second
  byte of each named token payload at tokenize time. We always write 0. This doesn't affect program semantics (AMOS
  recomputes these at load time), but the binary is not byte-identical.
- **OVERRIDE coverage**: there are ~139 dual-form pairs in core.bin; only those exercised by test files have been
  verified. New test programs may expose further incorrect form selections requiring new OVERRIDES entries.
- **Blank-line indent edge case**: AMOS editors occasionally save blank lines with `indent=0` rather than 1. These
  cannot be reproduced from ASCII source; the structural test skips indent comparison for empty lines.
