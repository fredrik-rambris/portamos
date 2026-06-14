---
layout: default
title: Sprite / Icon bank
parent: Banks
nav_order: 1
---

# Sprite / Icon bank

A Sprite bank (`AmSp`) holds sprites used with the `SPRITE` command; an Icon bank (`AmIc`) holds bobs used with the
`BOB` command. Both use the same file format and share a 32-entry Amiga colour palette.

Sprites/icons are always in chip RAM. The bank number is fixed: sprite banks are always bank 1, icon banks always bank
2.

## Commands

```bash
# Export to JSON + PNG sprite sheet
portamos disasm Sprites.Abk output/sprites.json

# Export with IFF ILBM sprite sheet instead of PNG
portamos disasm --ilbm Sprites.Abk output/sprites.json

# Reassemble
portamos asm output/sprites.json Sprites.Abk
```

## JSON schema

```json
{
  "type": "Sprite",
  "spritesheet": "sprites.png",
  "numColours": 16,
  "palette": [
    "#000000", "#ffffff", "#ff0000", "#00ff00"
  ],
  "sprites": [
    {
      "index": 0,
      "x": 0,
      "y": 0,
      "width": 16,
      "height": 16,
      "planes": 4,
      "hotspotX": 8,
      "hotspotY": 8
    },
    {
      "index": 1,
      "empty": true
    }
  ]
}
```

## Field reference

### Top level

| Field         | Type             | Description                                        |
|---------------|------------------|----------------------------------------------------|
| `type`        | string           | `"Sprite"` or `"Icon"`                             |
| `spritesheet` | string           | Path to PNG or IFF image file relative to the JSON |
| `numColours`  | integer          | Number of colours in the palette (`1 << planes`)   |
| `palette`     | array of strings | 32 hex RGB colours (`"#rrggbb"`)                   |

### `sprites[]`

| Field      | Type    | Description                                |
|------------|---------|--------------------------------------------|
| `index`    | integer | Sprite number within the bank (0-based)    |
| `empty`    | boolean | `true` for empty/placeholder slots         |
| `x`        | integer | X offset within the sprite sheet in pixels |
| `y`        | integer | Y offset within the sprite sheet in pixels |
| `width`    | integer | Width in pixels (always a multiple of 16)  |
| `height`   | integer | Height in pixels                           |
| `planes`   | integer | Colour depth in bitplanes (1–6)            |
| `hotspotX` | integer | Hot-spot X coordinate; omitted when 0      |
| `hotspotY` | integer | Hot-spot Y coordinate; omitted when 0      |

## Sprite sheet layout

Sprites are composited into a grid. The cell size is determined by the largest sprite in the bank. Non-empty sprites
fill left-to-right, top-to-bottom. Empty slots are not placed in the grid.

{: .note }
The importer reads each sprite's rectangle from the sheet using `x`, `y`, `width`, and `height`. You can rearrange
sprites in an image editor as long as you update the coordinates in the JSON to match.
