---
layout: default
title: build
parent: Commands
nav_order: 2
---

# `build` — Tokenize an ASCII source file

Reads an AMOS Professional ASCII source file and writes the corresponding binary `.AMOS` file. Optionally attaches bank
files directly or assembles them from JSON.

## Usage

```
portamos build <input.Asc> <output.AMOS> [options]
```

## Options

`--add-bank <file.Abk>`
: Attach an existing bank file to the output. Repeatable.

`--import-bank <bank.json>`
: Assemble a bank from a JSON metadata file and attach it to the output. Repeatable.

`--definition <path.json>`
: Load an additional extension definition file before tokenizing. Repeatable.

`--no-definition <id>`
: Skip a built-in extension definition by its ID. Repeatable.
Known built-in IDs: `Core`, `Music`, `Compact`, `Request`, `IOPorts`, `Compiler`.

`--fold`
: Mark all `Procedure` blocks as folded in the AMOS editor by default (bit 7 of the procedure flags byte). Without this
flag procedures are saved in the unfolded state.

## Examples

```bash
# Basic tokenization
portamos build program.Asc program.AMOS

# Attaching banks
portamos build program.Asc program.AMOS \
  --add-bank sprites.Abk \
  --add-bank music.Abk

# Assembling a bank from JSON and attaching it
portamos build program.Asc program.AMOS --import-bank sprites/bank.json

# Using a third-party extension definition
portamos build program.Asc program.AMOS --definition definitions/turboplus.json

# Replacing the built-in Music extension with AMCAF
portamos build program.Asc program.AMOS \
  --no-definition music \
  --definition definitions/amcaf.json

# Save with all procedures folded
portamos build program.Asc program.AMOS --fold
```
