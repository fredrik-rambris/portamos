---
layout: default
title: asm
parent: Commands
nav_order: 4
---

# `asm` — Assemble a bank from files

Reads the JSON metadata file produced by `disasm` and reassembles the binary `.Abk` file. The `"type"` field in the JSON
determines which importer is used. Data files are resolved relative to the JSON file's directory.

## Usage

```
portamos asm [options] <bank.json> <output.Abk>
```

## Options

`--optimize`
: Run the Pac.Pic palette optimizer before compressing. Only has an effect on Pac.Pic banks. Can also be set per-bank
via `"optimize": true` in the JSON.

`--no-optimize`
: Disable the palette optimizer even if `"optimize": true` is set in the JSON.

## Example

```bash
portamos asm output/sprites.json Sprites.Abk
```

## Bank JSON format

All bank JSON files share a common envelope:

```json
{
  "type": "...",
  "bankNumber": 1,
  "chipRam": false
}
```

The `"type"` field is set automatically by `disasm`. See the bank documentation for the full schema for each type:

| `"type"` value    | Bank                     | Schema                                                |
|-------------------|--------------------------|-------------------------------------------------------|
| `Music`           | Music bank               | [Music bank]({% link docs/banks/music.md %})          |
| `Samples`         | Sample bank              | [Sample bank]({% link docs/banks/sample.md %})        |
| `Tracker`         | ProTracker MOD           | [Tracker bank]({% link docs/banks/tracker.md %})      |
| `Sprite` / `Icon` | Sprite or icon bank      | [Sprite / Icon bank]({% link docs/banks/sprite.md %}) |
| `Resource`        | Resource bank            | [Resource bank]({% link docs/banks/resource.md %})    |
| `PacPic`          | Pac.Pic compressed image | [Pac.Pic bank]({% link docs/banks/pacpic.md %})       |
| `Amal`            | AMAL animation bank      | [AMAL bank]({% link docs/banks/amal.md %})            |
| `Menu`            | Menu bank                | [Menu bank]({% link docs/banks/menu.md %})            |
| `Work` / `Data`   | Raw data bank            | [Work / Data bank]({% link docs/banks/raw.md %})      |
