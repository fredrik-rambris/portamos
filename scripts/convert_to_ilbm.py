#!/usr/bin/env python3
"""
Convert upscaled indexed images to half-size IFF ILBM files.
Usage: python3 scripts/convert_to_ilbm.py
"""

import struct
import sys
from pathlib import Path

try:
    from PIL import Image
except ImportError:
    print("Pillow is required: pip install Pillow", file=sys.stderr)
    sys.exit(1)


def write_ilbm(out_path: Path, pixels: list[list[int]], palette: list[tuple],
               planes: int, width: int, height: int) -> None:
    """Write a raw (uncompressed) IFF ILBM file."""
    # Word-align each row
    row_bytes = (width + 15) // 16 * 2

    # Aspect ratio: NTSC 320×200 = 10:11; PAL 320×256 = 1:1 (10:10)
    x_asp, y_asp = (10, 11) if height == 200 else (10, 10)

    # BMHD chunk
    bmhd = struct.pack('>HHHHBBBBHBBHH',
                       width, height,   # w, h
                       0, 0,            # x, y
                       planes,          # nPlanes
                       0,               # masking: none
                       0,               # compression: none
                       0,               # pad
                       0,               # transparentColor
                       x_asp, y_asp,    # xAspect, yAspect
                       width, height)   # pageWidth, pageHeight

    # CMAP chunk – 3 bytes per entry
    cmap = b''.join(bytes([r, g, b]) for r, g, b in palette)

    # BODY chunk – interleaved scanlines, no compression
    body_rows = []
    for y in range(height):
        for p in range(planes):
            row = bytearray(row_bytes)
            for x in range(width):
                if pixels[y][x] & (1 << p):
                    row[x >> 3] |= 0x80 >> (x & 7)
            body_rows.append(bytes(row))
    body = b''.join(body_rows)

    def iff_chunk(tag: str, data: bytes) -> bytes:
        if len(data) & 1:
            data += b'\x00'
        return tag.encode('ascii') + struct.pack('>I', len(data)) + data

    ilbm_body = (iff_chunk('BMHD', bmhd)
                 + iff_chunk('CMAP', cmap)
                 + iff_chunk('BODY', body))

    form = b'FORM' + struct.pack('>I', len(ilbm_body) + 4) + b'ILBM' + ilbm_body
    out_path.write_bytes(form)
    print(f"  → {out_path}  ({width}×{height}, {planes} planes, "
          f"{len(palette)} colours, {len(form)} bytes)")


def load_frame(path: Path, frame: int = 0) -> Image.Image:
    """Open an image (or a specific GIF frame) in palette mode."""
    im = Image.open(path)
    if hasattr(im, 'n_frames') and im.n_frames > 1:
        im.seek(frame)
    if im.mode != 'P':
        raise ValueError(f"{path.name}: expected indexed (P) image, got {im.mode}")
    return im.copy()


def halve(im: Image.Image) -> tuple[list[list[int]], int, int]:
    """Nearest-neighbour 2× downsample of an indexed image (subsample, not average)."""
    w, h = im.size
    if w % 2 or h % 2:
        raise ValueError(f"Dimensions {w}×{h} are not both even – cannot halve exactly")
    data = list(im.getdata())
    nw, nh = w // 2, h // 2
    pixels = [[data[(y * 2) * w + (x * 2)] for x in range(nw)] for y in range(nh)]
    return pixels, nw, nh


def palette_from_image(im: Image.Image, n_colors: int) -> list[tuple]:
    raw = im.getpalette()          # flat [R,G,B, R,G,B, …] list of 768 bytes
    return [(raw[i * 3], raw[i * 3 + 1], raw[i * 3 + 2]) for i in range(n_colors)]


CONVERSIONS = [
    # (input_path, frame, n_colors, planes, output_path)
    ("src/test/resources/DefenderOfTheCrown2_Romantic_Fireplace.gif", 0, 32, 5,
     "src/test/resources/DefenderOfTheCrown2_Romantic_Fireplace.iff"),
    ("src/test/resources/DeviousDesigns_Level01.png", 0, 16, 4,
     "src/test/resources/DeviousDesigns_Level01.iff"),
    ("src/test/resources/DeviousDesigns_Level16.png", 0, 16, 4,
     "src/test/resources/DeviousDesigns_Level16.iff"),
    ("src/test/resources/Spherical.png", 0, 16, 4,
     "src/test/resources/Spherical.iff"),
]


def main() -> None:
    root = Path(__file__).parent.parent
    for src_rel, frame, n_colors, planes, dst_rel in CONVERSIONS:
        src = root / src_rel
        dst = root / dst_rel
        print(f"{src.name}  (frame {frame})")
        try:
            im = load_frame(src, frame)
            pixels, w, h = halve(im)
            palette = palette_from_image(im, n_colors)
            write_ilbm(dst, pixels, palette, planes, w, h)
        except Exception as e:
            print(f"  ERROR: {e}", file=sys.stderr)
            sys.exit(1)


if __name__ == '__main__':
    main()
