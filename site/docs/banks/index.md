---
layout: default
title: Banks
nav_order: 4
has_children: true
has_toc: false
---

# Banks

AMOS Professional stores all multimedia assets — sprites, music, samples, images, animations — in memory bank files (
`.Abk`). Multiple banks can be bundled into a bank set file (`.Abs`).

Portamos can export any bank to a JSON metadata file and associated data files in modern formats, and reassemble them
back into binary bank files.

## Workflow

```bash
# Export a bank to JSON + data files
portamos disasm input.Abk output/mybank.json

# Edit JSON / data files with any tool you like ...

# Reassemble back to binary
portamos asm output/mybank.json output.Abk
```

The `"type"` field in the JSON determines which reader/writer is used. All JSON files also carry `"bankNumber"` and
`"chipRam"` at the top level.

## Comments in JSON files

Portamos reads JSON with `//` and `/* */` comments enabled. You can annotate any field or section in your bank JSON
files:

```json5
{
  "type": "Samples",
  "bankNumber": 1,
  "chipRam": true,
  "samples": [
    // First sample: main theme loop
    {
      "name": "Theme",
      "frequencyHz": 8363,
      // Amiga standard tuning
      "file": "samples-sample000.wav"
    }
  ]
}
```

Comments are stripped before parsing and are never written back by `disasm`.

## Supported bank types

| Type                                             | JSON `"type"`     | Description                                    |
|--------------------------------------------------|-------------------|------------------------------------------------|
| [Sprite / Icon]({% link docs/banks/sprite.md %}) | `Sprite` / `Icon` | Sprite or icon bank with palette               |
| [Sample]({% link docs/banks/sample.md %})        | `Samples`         | 8-bit PCM samples with name and frequency      |
| [Pac.Pic]({% link docs/banks/pacpic.md %})       | `PacPic`          | Compressed Amiga screen image                  |
| [AMAL]({% link docs/banks/amal.md %})            | `Amal`            | AMAL animation scripts and movement recordings |
| [Tracker]({% link docs/banks/tracker.md %})      | `Tracker`         | ProTracker / MOD file                          |
| [Resource]({% link docs/banks/resource.md %})    | `Resource`        | Sprites + palette + optional AMUI programs     |
| [Music]({% link docs/banks/music.md %})          | `Music`           | AMOS tracker: instruments, songs and patterns  |
| [Menu]({% link docs/banks/menu.md %})            | `Menu`            | AMOS menu definitions                          |
| [Work / Data]({% link docs/banks/raw.md %})      | `Work` / `Data`   | Raw binary data banks                          |
