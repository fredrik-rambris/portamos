---
layout: default
title: Work / Data bank
parent: Banks
nav_order: 9
---

# Work / Data bank

Work and Data banks hold raw binary data with no internal structure. Other raw bank types (`Asm`, `Code`, `Datas`) use
the same format.

## Commands

```bash
# Export raw bytes to JSON + .bin file
portamos disasm Data.Abk output/data.json

# Reassemble
portamos asm output/data.json Data.Abk

# Wrap an existing binary file in a bank envelope
portamos raw payload.bin output.Abk --type WORK
portamos raw payload.bin output.Abk --type DATA --bank-number 2
portamos raw payload.bin output.Abk --type WORK --chip
```

## JSON schema

```json
{
  "type": "Work",
  "bankNumber": 1,
  "chipRam": false,
  "dataFile": "data.bin"
}
```

| Field        | Type    | Description                                         |
|--------------|---------|-----------------------------------------------------|
| `type`       | string  | `"Work"`, `"Data"`, `"Asm"`, `"Code"`, or `"Datas"` |
| `bankNumber` | integer | Bank slot (1-based)                                 |
| `chipRam`    | boolean | `true` = chip RAM, `false` = fast RAM               |
| `dataFile`   | string  | Path to the raw binary file relative to the JSON    |

## `raw` command

For wrapping a pre-built binary without going through `disasm`/`asm`:

```bash
portamos raw payload.bin output.Abk --type WORK
portamos raw payload.bin output.Abk --type MUSIC --chip --bank-number 3
```

Valid `--type` values: `WORK`, `DATA`, `MUSIC`, `SAMPLES`, `ASM`, `CODE`, `AMAL`, `MENU`, `TRACKER`, `DATAS`.
