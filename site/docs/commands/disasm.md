---
layout: default
title: disasm
parent: Commands
nav_order: 3
---

# `disasm` — Export a bank to files

Exports the contents of any supported bank type to a JSON metadata file. All associated data files are written alongside
the JSON, using its stem as a filename prefix.

Also accepts `.Abs` bank set files — each bank in the set is exported to a separate JSON file.

## Usage

```
portamos disasm [options] <input.Abk|input.Abs> <output/mybank.json>
```

## Options

`--ilbm`
: Export images as IFF ILBM (`.iff`) instead of PNG.

`--8svx`
: Export audio samples as IFF 8SVX (`.8svx`) instead of WAV.

`--sample-names`
: Name audio files after the sample names stored in the bank rather than using the stem + index pattern.

## Output files by bank type

| Bank type     | Data files                                                                         | Schema                                                |
|---------------|------------------------------------------------------------------------------------|-------------------------------------------------------|
| Resource      | `<stem>.png` (or `.iff`), `<stem>-program000.amui` …                               | [Resource bank]({% link docs/banks/resource.md %})    |
| Sprite / Icon | `<stem>.png` (or `.iff`)                                                           | [Sprite / Icon bank]({% link docs/banks/sprite.md %}) |
| Pac.Pic       | `<stem>.png` (or `.iff`)                                                           | [Pac.Pic bank]({% link docs/banks/pacpic.md %})       |
| Music         | `<stem>-instrument000.wav` (or `.8svx`) …                                          | [Music bank]({% link docs/banks/music.md %})          |
| Sample        | `<stem>-sample000.wav` (or `.8svx`) …                                              | [Sample bank]({% link docs/banks/sample.md %})        |
| Tracker       | `<stem>.mod`                                                                       | [Tracker bank]({% link docs/banks/tracker.md %})      |
| AMAL          | `<stem>-program000.amal` …, `<stem>-movement000.json` …, `<stem>-environment.amal` | [AMAL bank]({% link docs/banks/amal.md %})            |
| Menu          | *(JSON only)*                                                                      | [Menu bank]({% link docs/banks/menu.md %})            |
| Work / Data   | `<stem>.bin`                                                                       | [Work / Data bank]({% link docs/banks/raw.md %})      |

## Examples

```bash
# Export a sprite bank to PNG + JSON
portamos disasm Sprites.Abk output/sprites.json

# Export as IFF ILBM instead of PNG
portamos disasm --ilbm Sprites.Abk output/sprites.json

# Export a music bank with 8SVX samples
portamos disasm --8svx Music.Abk output/music.json

# Export a sample bank and name files after sample names
portamos disasm --sample-names Samples.Abk output/samples.json

# Export a bank set (.Abs)
portamos disasm AllBanks.Abs output/banks.json
```
