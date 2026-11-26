---
layout: default
title: ilbm
parent: Commands
nav_order: 7
---

# `ilbm` — Convert an image to IFF ILBM

Converts an indexed-colour image (8-bit indexed PNG, or an existing IFF ILBM) to a ByteRun1-compressed IFF ILBM. With
`--optimize` the palette is reordered first to find the colour order that gives the smallest compressed image.

## Usage

```
portamos ilbm [options] <input> <output.iff>
```

The input format is detected from the file extension: `.iff` / `.ilbm` are read as ILBM, everything else as PNG.

## Options

`--optimize`
: Run the palette optimizer before compressing. Reordering the palette does not change how the image looks, but it
changes which bitplanes are uniform and therefore how well ByteRun1 can compress them. Slower, smaller output.

`--planes <n>`
: Number of bitplanes to write. Defaults to the smallest number that holds the palette and every colour index used in
the image.

## Notes

- When the source is an ILBM, its `CAMG` display mode (hires, interlace, …) and transparent colour are kept. The
  transparent colour index is remapped if the palette is reordered.
- HAM and Extra-HalfBrite source images are rejected: their bitplane values are not plain palette indices.
- The palette is padded with black (or truncated) to exactly `2^planes` entries.

## Palette optimizer results

Compressed `BODY` size of some 320×200 / 320×256 game and scene images:

| Image                   | Plain    | Optimized | Saving |
|-------------------------|----------|-----------|--------|
| 320×200 32-colour scene | 34 563 B | 25 935 B  | 25%    |
| 320×200 16-colour level | 24 359 B | 20 409 B  | 16%    |
| 320×200 16-colour level | 24 778 B | 21 256 B  | 14%    |
| 320×256 16-colour game  | 18 816 B | 16 637 B  | 12%    |

## Examples

```bash
# PNG to compressed ILBM
portamos ilbm picture.png picture.iff

# Recompress an existing ILBM with an optimized palette
portamos ilbm --optimize original.iff smaller.iff
```
