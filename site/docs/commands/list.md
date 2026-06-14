---
layout: default
title: list
parent: Commands
nav_order: 1
---

# `list` — Detokenize a binary to ASCII source

Reads a binary `.AMOS` file and writes the corresponding AMOS Professional ASCII source.

Compiled procedures (produced by the AMOSPro Compiler) are preserved as PEM-style base64 blocks in the output so that
the source can be round-tripped back through `build` without losing the compiled body.

## Usage

```
portamos list <input.AMOS> <output.Asc> [options]
```

## Options

`--definition <path.json>`
: Load an additional extension definition file before detokenizing. Repeatable. Use this for third-party extensions not
included in the built-in set.

`--no-definition <id>`
: Skip a built-in extension definition by its ID. Repeatable. Use this when a program replaces a built-in extension with
a third-party one at runtime.
Known built-in IDs: `Core`, `Music`, `Compact`, `Request`, `IOPorts`, `Compiler`.

## Examples

```bash
# Basic detokenization
portamos list program.AMOS program.Asc

# With a third-party extension definition
portamos list program.AMOS program.Asc --definition definitions/turboplus.json

# Replacing the built-in Music extension with AMCAF
portamos list program.AMOS program.Asc \
  --no-definition music \
  --definition definitions/amcaf.json
```
