# Extension Definition Files

This directory contains token definition files for third-party AMOS Professional extensions
that are not included in the built-in set. Load them at build time with the `--definition`
option:

```bash
portamos build source.Asc output.AMOS --definition definitions/turboplus.json
```

Multiple `--definition` flags can be combined:

```bash
portamos build source.Asc output.AMOS \
  --definition definitions/turboplus.json \
  --definition definitions/myextension.json
```

---

## Included files

| File             | Extension  | Slot | Vendor       |
|------------------|------------|------|--------------|
| `turboplus.json` | TURBO Plus | 12   | Manuel Andre |

---

## Creating a definition file for a new extension

These definitions share the same format as the [IntelliJ AMOS plug-in](https://github.com/fredrik-rambris/intellij-amos)
The parameter names, links and documentation are used to display inline documentation in IntelliJ IDEA.

### Step 1 — find the slot number

Each extension occupies a fixed numbered slot. The five built-in extensions use slots 0–3 and 6.
Third-party extensions typically use slots 7 and above. The AMOS Pro manual instructs developers
to pick slots from the bottom up. The slot number is usually documented
with the extension, or visible in the extension's loader code.

### Step 2 — generate a skeleton from the `.Lib` binary

```bash
portamos gen-ext-json AMOSPro_MyExt.Lib --slot <n> myext.json
```

This reads the token table embedded in the `.Lib` file and produces a JSON skeleton with one
entry per token, using the correct `offset` values needed for encoding. The default `--start`
offset is `6` for all extensions (use `-194` only for the core slot 0).

### Step 3 — fill in the skeleton

The generated file has placeholder names like `"CMD_0006"` for every entry whose name could
not be recovered from the binary. Open the file and:

- **Replace command names** with the actual keyword or function name (uppercase, as you would
  type it in AMOS source). Multi-word keywords like `"MULTI YES"` are written as a single
  string with a space.
- **Set `kind`** to one of: `instruction`, `function`, `structure`, `operator`.
- **Add `documentation`** — a short description of what the command does.
- **Add `link`** — direct link to online documentation if it exists.
  > **Note:** documentation, link and parameter names are for human reference only; they do not
  > affect tokenization.
- **Fill in `parameters`** for each signature if the command takes arguments. Each parameter
  is either:
    - `{"kind": "value", "name": "...", "valueType": "integer"|"float"|"string"}` — a value
      argument that counts toward `commaGroups`
    - `{"kind": "keyword", "keyword": "To"}` — an embedded keyword separator that does **not**
      count toward `commaGroups`
- **Set `commaGroups`** explicitly on each signature if the auto-computed value (number of
  `value` parameters minus number of `keyword` parameters) is wrong. See
  [Signature selection](#signature-selection) below.
- **Add `extraBytes`** (at the definition level, not per-signature) if the token reserves
  zero-bytes for runtime back-patching. Common values: `2` for loop heads, `4` for branching
  instructions.

### Step 4 — test

Build a source file that uses the extension and confirm portamos encodes it without errors.
Use `portamos dump output.AMOS` to verify the token stream looks correct.

---

## File format reference

```json
{
  "extension": {
    "id": "MyExtension",
    "name": "Human-readable name",
    "filename": "AMOSPro_MyExt.Lib",
    "slot": 4,
    "start": 6,
    "vendor": "Author Name"
  },
  "definitions": [
    {
      "name": "MY COMMAND",
      "kind": "instruction",
      "documentation": "Does something useful.",
      "signatures": [
        {
          "offset": 6,
          "commaGroups": 0,
          "presentation": "My Command",
          "parameters": []
        }
      ]
    },
    {
      "name": "MY FUNC",
      "kind": "function",
      "documentation": "Returns something useful.",
      "extraBytes": 0,
      "signatures": [
        {
          "offset": 22,
          "commaGroups": 1,
          "presentation": "My Func(value)",
          "parameters": [
            {
              "kind": "value",
              "name": "value",
              "valueType": "integer"
            }
          ]
        }
      ]
    }
  ]
}
```

### `extension` fields

| Field      | Required | Description                                                                          |
|------------|----------|--------------------------------------------------------------------------------------|
| `id`       | yes      | Short identifier (no spaces)                                                         |
| `slot`     | yes      | Extension slot number (determines encoding prefix)                                   |
| `start`    | yes      | Byte offset from token-table base to first entry; always `6` for non-core extensions |
| `name`     | no       | Human-readable name (not used by the tokenizer)                                      |
| `filename` | no       | Original `.Lib` filename (not used by the tokenizer)                                 |
| `vendor`   | no       | Author or publisher (not used by the tokenizer)                                      |

### `definitions` entry fields

| Field           | Required          | Description                                                |
|-----------------|-------------------|------------------------------------------------------------|
| `name`          | yes               | Keyword in uppercase, as written in AMOS source            |
| `kind`          | yes               | `instruction`, `function`, `structure`, or `operator`      |
| `signatures`    | yes               | List of binary forms (one per argument-count variant)      |
| `documentation` | no — add manually | Description of what the command does                       |
| `link`          | no — add manually | Direct URI to external documentation, if any               |
| `extraBytes`    | no                | Extra zero bytes after the token for runtime back-patching |

### `signatures` entry fields

| Field          | Required          | Description                                                          |
|----------------|-------------------|----------------------------------------------------------------------|
| `offset`       | yes               | Byte offset within the extension's token table (from `gen-ext-json`) |
| `commaGroups`  | yes               | Number of top-level comma groups expected after the keyword          |
| `presentation` | no — add manually | Display form for documentation purposes                              |
| `parameters`   | no — add manually | Parameter list (see above); used to compute `commaGroups` if omitted |

> **Note:** `presentation`, `parameters`, `link`, and `documentation` are informational only.
> The tokenizer uses only `name`, `offset`, `slot`, and `commaGroups`.

---

## Signature selection

When a keyword has multiple binary forms (e.g. with and without an optional argument),
each form gets its own entry in `signatures` with a different `offset` and `commaGroups`.
The tokenizer counts how many top-level comma groups follow the keyword on the source line
and picks the signature whose `commaGroups` is the highest value still ≤ that count,
falling back to the first signature if none qualify.

Example — `MY RANGE` has a two-argument form and a three-argument form:

```json
"signatures": [
{"offset": 6, "commaGroups": 2},
{"offset": 22, "commaGroups": 3}
]
```

`My Range 0,10` → 2 comma groups → offset 6.
`My Range 0,10,2` → 3 comma groups → offset 22.
