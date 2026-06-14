---
layout: default
title: Resource bank
parent: Banks
nav_order: 6
---

# Resource bank

A Resource bank holds graphical elements (sprites/icons stored as Pac.Pic compressed images), a shared palette, optional
text strings, and optional DBL Interface programs (`.amui` files). Used by `RESOURCE` commands and the AMOS screen
designer.

## Commands

```bash
# Export to JSON + PNG + programs
portamos disasm Resource.Abk output/resource.json

# Export with IFF ILBM instead of PNG
portamos disasm --ilbm Resource.Abk output/resource.json

# Reassemble
portamos asm output/resource.json Resource.Abk
```

## JSON schema

```json
{
  "type": "Resource",
  "bankNumber": 1,
  "chipRam": true,
  "screenMode": "LORES",
  "imagePath": "",
  "spritesheet": "resource.png",
  "numColours": 16,
  "palette": ["#000000", "#ffffff"],
  "elements": [
    {
      "name": null,
      "type": null,
      "images": [
        { "x": 0, "y": 0, "width": 16, "height": 16 }
      ]
    },
    {
      "name": "BUTTON",
      "type": "SINGLE",
      "images": [
        { "x": 16, "y": 0, "width": 32, "height": 16 }
      ]
    }
  ],
  "texts": [
    { "index": 0, "text": "Hello World" }
  ],
  "programs": [
    { "index": 0, "file": "resource-program000.amui" }
  ]
}
```

## Field reference

### Top level

| Field         | Type             | Description                                                        |
|---------------|------------------|--------------------------------------------------------------------|
| `type`        | string           | Always `"Resource"`                                                |
| `bankNumber`  | integer          | Bank slot (1-based)                                                |
| `chipRam`     | boolean          | `true` = chip RAM, `false` = fast RAM                              |
| `screenMode`  | string           | Amiga screen mode stored in the bank header                        |
| `imagePath`   | string           | Original source image path as stored in the binary (informational) |
| `spritesheet` | string           | Path to the image file relative to the JSON                        |
| `numColours`  | integer          | Number of colours in the palette                                   |
| `palette`     | array of strings | Hex RGB colours (`"#rrggbb"`)                                      |

### `elements[]`

Each element is one entry in the binary offset table.

| Field             | Type           | Description                                                |
|-------------------|----------------|------------------------------------------------------------|
| `name`            | string or null | `null` for simple images; up to 8 chars for named elements |
| `type`            | string or null | `null`, `"SINGLE"`, `"LINE"`, or `"BOX"`                   |
| `images[]`        | array          | One or more image rectangles within the sprite sheet       |
| `images[].x`      | integer        | X offset in the sprite sheet                               |
| `images[].y`      | integer        | Y offset in the sprite sheet                               |
| `images[].width`  | integer        | Width in pixels                                            |
| `images[].height` | integer        | Height in pixels                                           |

### `texts[]`

| Field   | Type    | Description      |
|---------|---------|------------------|
| `index` | integer | Text slot number |
| `text`  | string  | Text content     |

### `programs[]`

| Field   | Type    | Description                                           |
|---------|---------|-------------------------------------------------------|
| `index` | integer | Program slot number                                   |
| `file`  | string  | Path to the `.amui` program file relative to the JSON |
