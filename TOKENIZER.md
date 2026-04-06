# AMOS Tokenizer — How it Works

This document describes the ASCII → binary tokenization pipeline: how an AMOS Professional source file (`.Asc`) is parsed into an intermediate representation and then encoded into the binary `.AMOS` format.

---

## Pipeline overview

```
ASCII source (.Asc)
        │
        ▼
   [Pre-scan]          Collect procedure names and array variable names
        │
        ▼
  [AsciiParser]        Each source line → list of AmosTokens
        │
        ▼
  [BinaryEncoder]      Each AmosLine → binary bytes
        │
        ▼
  [AmosFileWriter]     All lines → complete .AMOS file
        │
        ▼
  binary file (.AMOS)
```

---

## Step 1 — Pre-scan

Before any line is parsed, the entire source is scanned twice:

- **Procedure names**: Every line matching `Procedure NAME[...]` records `NAME` (lowercased) in a set.
- **Array variable names**: Every line matching `Dim NAME(...)` records `NAME` (lowercased, with type suffix if present).

These sets are passed into the parser and used during identifier classification (see [Identifier tokens](#identifier-tokens) below). The pre-scan is necessary because AMOS source is not declared-before-use for procedure calls, and `Dim` may appear anywhere.

---

## Step 2 — Line parsing (ASCII → tokens)

Each source line is parsed independently. The first step is to measure leading spaces and convert them to an indent level:

```
indent = leadingSpaces + 1
```

AMOS uses 1-based indent (level 1 = no indentation). Each leading space is one indent level.

The stripped line is then parsed into a sequence of `AmosToken` values.

### Recognition order

The parser processes characters left-to-right with the following priority:

1. **Leading `'`** — whole-line single-quote REM: the rest of the line is comment text. No further parsing.
2. **`Rem ` at line start** (case-insensitive, followed by a space) — Rem keyword comment. No further parsing.
3. **Double-quoted strings** (`"..."`) — consumed as a string literal.
4. **Single-quoted strings** (`'...'`, mid-line) — consumed as a string literal.
5. **Inline `Rem`** — if `Rem` appears mid-line (followed by space or end of input), it consumes the rest of the line as a Rem token and stops parsing.
6. **Numeric literals** — `$hex`, `%binary`, decimal integer, or float (see [Numeric tokens](#numeric-tokens)).
7. **Keywords** — maximal munch, up to 3 space-separated words (see [Keyword tokens](#keyword-tokens)).
8. **Operators / punctuation** — two-char compound operators tried first (`>=`, `<=`, `<>`, `><`, `=<`, `=>`), then single-char.
9. **Identifiers** — variables, labels, procedure calls, label references (see [Identifier tokens](#identifier-tokens)).

Whitespace between tokens is skipped. Unknown characters are skipped silently to avoid infinite loops.

### Numeric tokens

| Source syntax | Token type | Notes |
|---|---|---|
| `$BEEF` | `HexInt` | Leading `$` |
| `%1010` | `BinaryInt` | Leading `%` |
| `42` | `DecimalInt` | Plain digits |
| `3.14` | `Flt` | Decimal point present |
| `9.22 E+18` | `Flt` | AMOS prints a space before the exponent |
| `3.14#` | `Dbl` | Trailing `#` suffix |

Negative numbers are never emitted as negative literals. A leading `-` produces a minus operator token followed by a positive literal (matching how AMOS itself encodes them).

### Keyword tokens

The parser attempts to match up to three consecutive space-separated words against the token table. Longest match wins ("maximal munch"): if `Screen Width` matches, `Screen` alone is not used even if it also exists.

Word characters are letters, digits, `_`, `$`, and `#`. A `#` terminates the word (so `Input #` is one word).

After matching a keyword name, the parser counts the **comma groups** that follow it on the source line. This count is used to select the correct binary form for keywords that have multiple signatures (see [Multi-form keywords](#multi-form-keywords)).

Some keywords carry per-line context flags that affect how the next identifier is classified:

| Keyword | Effect on next identifier |
|---|---|
| `Goto`, `Gosub`, `Resume`, `Pop Proc` | Next identifier is a `LabelRef` |
| `Dim` | Next variable gets the array flag |
| `Procedure` | Next identifier is the procedure-definition variable (flags = `0x80`) |

These flags are reset after each identifier is consumed, or at the start of each new line.

Extension keywords (from `.Lib` files) match exactly the same way. The token table transparently maps them to `ExtKeyword(slot, offset)` tokens.

### Identifier tokens

After keyword matching fails, identifiers are classified by context:

| Condition | Token type |
|---|---|
| Name followed by `:` (first token on line) | `Label` — a label definition |
| After `Procedure` keyword | `Variable` with flags `0x80` — the proc's own name |
| After `Goto` / `Gosub` / `Resume` | `LabelRef` — a goto/gosub target |
| Name is in the pre-scanned procedure set, or followed by `[` | `ProcRef` — a procedure call |
| After `Dim`, or name is in the pre-scanned array set | `Variable` with array flag (`0x40`) |
| Otherwise | `Variable` |

Variable types are determined by a suffix on the name:
- No suffix → INTEGER (`0x00`)
- `$` suffix → STRING (`0x02`)
- `#` suffix → FLOAT (`0x01`)

---

## Step 3 — Binary encoding (tokens → bytes)

Each line is encoded as:

```
[1 byte]  line length in 16-bit words (covers entire line including header + EOL)
[1 byte]  indent level
[n bytes] token stream
[2 bytes] 0x0000  end-of-line marker
```

All multi-byte integers are big-endian. The total byte count of a line must always be even; odd-length payloads are padded with a `0x00` byte.

### Token encoding

**Comments (Rem / SingleQuoteRem)**
```
[token:2]  $064A (Rem) or $0652 (')
[00:1]     unused
[len:1]    number of text bytes
[text:n]
[pad:0-1]  0x00 if n is odd
```
The EOL's first `0x00` byte acts as a null terminator for the text.

**Quoted strings**
```
[token:2]  $0026 (double-quote) or $002E (single-quote)
[len:2]    text length, big-endian
[text:n]
[pad:0-1]  0x00 if n is odd
```

**Integer literals**
```
[token:2]  $003E (decimal), $0036 (hex), $001E (binary)
[value:4]  signed 32-bit big-endian
```

**Single-precision float (AMOS custom format)**
```
[0046:2]
[value:4]  AMOS FFP encoding (see below)
```

AMOS does not use IEEE 754 for single-precision floats. The 32-bit encoding is:
- bits 31–8: 24-bit mantissa (MSB always set for non-zero values)
- bit 7: unused
- bits 6–0: 7-bit exponent, biased by 88

Conversion from IEEE 754: `amosExp = ieeeUnbiasedExp + 65`

Negative floats do not exist as literals; they are encoded as a unary minus operator followed by a positive float token.

**Double-precision float**
```
[2B6A:2]
[value:8]  standard IEEE 754 double, big-endian
```

**Named tokens** (Variable, Label, ProcRef, LabelRef)
```
[token:2]  $0006 / $000C / $0012 / $0018
[00:1]     unused
[00:1]     symbol-table slot offset (written as 0; AMOS fills this at load time)
[n:1]      name length rounded up to even
[flags:1]
[name:n]   lowercase ASCII; odd-length names have a null terminator in the padding byte
```

Variable flags byte:
- bits 1–0: type — `0x00` INTEGER, `0x01` FLOAT, `0x02` STRING
- bit 6: array flag (`0x40`)
- bit 7: procedure-definition flag (`0x80`)

**Core keyword**
```
[offset:2]   raw 16-bit token offset
[zeros:0-8]  back-patch space (see below)
```

**Extension keyword**
```
[004E:2]
[slot:1]
[00:1]    unused
[offset:2]  signed 16-bit offset within the extension's token table
```

### Back-patch bytes

Certain control-flow keywords require extra zero bytes immediately after the token. AMOS fills these in at load time with jump addresses or other runtime values:

| Keywords | Extra bytes |
|---|---|
| `For`, `Repeat`, `While`, `Do`, `If`, `Else`, `Data`, `Else If` | 2 |
| `Exit`, `Exit If`, `On` | 4 |
| `Procedure` | 8 |
| `Equ`, `Lvo`, `Struc`, `Struc$` | 6 |

---

## Step 4 — File assembly

The complete binary file layout:

```
[16 bytes]  version header
              "AMOS Basic v1.3 "  (BASIC_13)
              "AMOS Basic  v1.34" (BASIC_134)
              "AMOS Pro101v\0\0\0\0" (PRO_101)
[4 bytes]   code section length in bytes (uint32 big-endian)
[n bytes]   code section: concatenated encoded lines
[4 bytes]   "AmBs" magic
[2 bytes]   bank count (uint16 big-endian)
[...]       bank data (if any)
```

---

## Token table / JSON definitions

The token table is built at startup from JSON definition files in `src/main/resources/amos/definitions/`:

| File | Extension slot | Notes |
|---|---|---|
| `core.json` | 0 | Core AMOS keywords |
| `music.json` | 1 | Music extension |
| `compact.json` | 2 | Compact extension |
| `request.json` | 3 | Request extension |
| `ioports.json` | 6 | IOPorts extension |
| `compiler.json` | — | Compiler extension |

Operators (`+`, `-`, `=`, `<>`, etc.) and punctuation (`,`, `;`, `(`, `)`, etc.) are hardcoded; they do not appear in the JSON files.

A definition without an `"offset"` field in any of its signatures is documentation-only and is skipped by the tokenizer.

### Key encoding

Internally, every token is represented as a single integer key:

```
key = (slot << 16) | offset
```

- **slot 0** (core): the key's lower 16 bits are written directly as a `uint16`.
- **slot N** (extension): encoded as `ExtKeyword(slot, offset)` → `$004E slot $00 offset`.

### Multi-form keywords

Many AMOS keywords have multiple binary table entries — one per argument-count variant. Examples:

- `Paint x,y` vs `Paint x,y,colour` — 1 and 2 comma groups
- `X Screen(x)` vs `X Screen(screen,x)` — 1 and 2 comma groups

The parser counts "comma groups" after a keyword: top-level commas (not inside parentheses or brackets) plus one, if any argument is present. Zero means no arguments.

Selection rule: from the sorted list of signatures, pick the one with the highest `commaGroups` value that is still ≤ the actual count. If none qualify, fall back to the first (fewest-arguments) signature.

`commaGroups` is derived from each signature's parameter list by counting `value`-kind parameters minus `keyword`-kind parameters. Manual overrides exist in `scripts/migrate_offsets.py` for keywords where the automatic derivation is wrong.

---

## Debugging

Use the built-in diff and dump tools rather than raw binary comparisons:

```bash
# Dump a binary file as a token listing
./gradlew run --args="--dump file.AMOS"

# Token-level diff between expected and actual
./gradlew run --args="--diff expected.AMOS actual.AMOS"
```

The diff output shows the token type, decoded payload, and raw hex for each diverging position, making it straightforward to identify which keyword form was selected incorrectly or which literal value was encoded differently.
