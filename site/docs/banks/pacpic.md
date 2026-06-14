---
layout: default
title: Pac.Pic bank
parent: Banks
nav_order: 3
---

# Pac.Pic bank

A Pac.Pic bank holds a single compressed Amiga screen image. There are two variants:

- **PACK** — plain compressed image. Load it with `Load` and unpack with `Unpack <bankno>`. You must set up the
  destination screen yourself before calling `Unpack`.
- **SPACK** — compressed image with an embedded screen header carrying palette, resolution and screen coordinates. Use
  `Unpack <bankno> To <screenno>` to create and configure the screen automatically, including loading the palette.

## Commands

```bash
# Export to JSON + PNG
portamos disasm Picture.Abk output/picture.json

# Export with IFF ILBM instead of PNG
portamos disasm --ilbm Picture.Abk output/picture.json

# Reassemble
portamos asm output/picture.json Picture.Abk

# Reassemble with palette optimizer (smaller file, slower build)
portamos asm --optimize output/picture.json Picture.Abk
```

## JSON schema

### SPACK bank — minimal (auto-fill from image)

The simplest SPACK configuration: just include an empty `screen` object and all fields are derived from the image file's
dimensions, colour count, and embedded CMAP palette.

```json5
{
  "type": "PacPic",
  "bankNumber": 1,
  "chipRam": false,
  "imageFile": "picture.png",
  "screen": {} // all screen fields filled in automatically from the image
}
```

### SPACK bank — explicit screen header

Override individual fields when the image metadata is insufficient or when you need non-standard screen parameters.

```json5
{
  "type": "PacPic",
  "bankNumber": 1,
  "chipRam": false,
  "imageFile": "picture.png",
  "optimize": true, // run palette optimizer for smaller output
  "screen": {
    "width": 320,
    "height": 200,
    "displayWidth": 320,
    "displayHeight": 200,
    "numPlanes": 4,
    "numColors": 16,
    "bplCon0": 16896, // (numPlanes << 12) | 0x0200 — standard lores + COLOR flag
    "hardX": 129,     // 0x81 — standard AMOS lores screen start X
    "hardY": 50,      // 0x32 — standard AMOS lores screen start Y
    "palette": [0, 4095, 3840, 240] // Amiga 0x0RGB colour words
  }
}
```

### PACK bank (no screen header)

```json5
{
  "type": "PacPic",
  "bankNumber": 1,
  "chipRam": false,
  "imageFile": "picture.png"
  // no "screen" key — plain PACK bank; use Unpack <bankno> after setting up the screen yourself
}
```

Omit `"screen"` entirely for a plain PACK bank.

## Field reference

### Top level

| Field        | Type    | Description                                                             |
|--------------|---------|-------------------------------------------------------------------------|
| `type`       | string  | Always `"PacPic"`                                                       |
| `bankNumber` | integer | Bank slot (1-based)                                                     |
| `chipRam`    | boolean | `true` = chip RAM, `false` = fast RAM (default)                         |
| `imageFile`  | string  | Path to PNG or IFF image file relative to the JSON                      |
| `optimize`   | boolean | Run palette optimizer before compressing (slower build, smaller output) |
| `planes`     | integer | Override bit-depth (normally inferred from the image's colour count)    |

### `screen` object

Include this object for SPACK banks. An empty object `{}` is valid — all fields default automatically from the image.

| Field           | Auto-default                  | Description                                       |
|-----------------|-------------------------------|---------------------------------------------------|
| `width`         | image width                   | Width in pixels                                   |
| `height`        | image height                  | Height in pixels                                  |
| `displayWidth`  | image width                   | Displayed width                                   |
| `displayHeight` | image height                  | Displayed height                                  |
| `numPlanes`     | derived from colour count     | Number of bitplanes                               |
| `numColors`     | `1 << numPlanes`              | Number of palette entries                         |
| `bplCon0`       | `(numPlanes << 12) \| 0x0200` | Amiga `BPLCON0` register value                    |
| `hardX`         | `0x81` (129)                  | Screen start X (standard AMOS lores)              |
| `hardY`         | `0x32` (50)                   | Screen start Y (standard AMOS lores)              |
| `palette`       | from image CMAP               | Array of Amiga 12-bit colours (`0x0RGB` integers) |

## Palette optimizer

The `--optimize` flag (or `"optimize": true` in the JSON) runs a greedy pairwise palette-swap search before compressing.
It does not affect image appearance but can significantly reduce compressed size, at the cost of longer assembly time.

| Image                   | Baseline | Optimized | Saving |
|-------------------------|----------|-----------|--------|
| 320×200 32-colour scene | 27 416 B | 18 812 B  | 31%    |
| 320×200 16-colour level | 17 248 B | 13 242 B  | 23%    |
| 320×256 16-colour game  | 12 230 B | 10 226 B  | 16%    |
