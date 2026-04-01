#!/usr/bin/env python3
"""
Enriches the amiga-amos JSON definition files with binary token offsets and extraBytes.

For each known extension, reads the corresponding .bin file from src/main/resources/amos/extensions/,
extracts the name→offset mapping using the same algorithm as TokenTable.java, and writes the
offset (and extraBytes where applicable) back into the JSON definition file.

Run from the portamos project root:
    python3 scripts/enrich_definitions.py
"""
import json
import struct
import sys
from pathlib import Path

# Extra zero bytes written after certain core tokens (back-patch space).
# Keyed by the raw uint16 offset within the core token table.
EXTRA_BYTES = {
    0x023C: 2,  # For
    0x0250: 2,  # Repeat
    0x0268: 2,  # While
    0x027E: 2,  # Do
    0x02BE: 2,  # If
    0x02D0: 2,  # Else
    0x0404: 2,  # Data
    0x25A4: 2,  # Else If
    0x0290: 4,  # Exit If
    0x029E: 4,  # Exit
    0x0316: 4,  # On
    0x0376: 8,  # Procedure
    0x2A40: 6,  # Equ
    0x2A4A: 6,  # Lvo
    0x2A54: 6,  # Struc
    0x2A64: 6,  # Struct
}


def leek(d: bytes, o: int) -> int:
    return struct.unpack_from('>I', d, o)[0]


def deek(d: bytes, o: int) -> int:
    return struct.unpack_from('>H', d, o)[0]


def decode_name(raw: bytes) -> str | None:
    """Mirror of TokenTable.decodeName in Java."""
    if not raw:
        return None
    if raw[0] & 0xFF == 0x80:
        return None  # sentinel: reuse lastName
    sb = []
    cap = True
    for b in raw:
        c = chr(b & 0x7F)
        if cap and c.islower():
            c = c.upper()
        cap = (c == ' ')
        sb.append(c)
    s = ''.join(sb)
    return s.rstrip(' ')


def parse_extension(data: bytes, start: int) -> list[tuple[str, int]]:
    """
    Returns ordered list of (normalized_uppercase_name, offset) pairs,
    deduplicated to first occurrence only (matching putIfAbsent behaviour).
    """
    if len(data) < 54:
        return []
    if leek(data, 0) != 0x3F3:
        print("  ERROR: bad hunk header", file=sys.stderr)
        return []
    if leek(data, 24) != 0x3E9:
        print("  ERROR: bad code hunk", file=sys.stderr)
        return []

    tkoff = leek(data, 32) + 32 + 18
    if leek(data, 32 + 18) == 0x41503230:  # AP20 tag
        tkoff += 4

    last_name: str | None = None
    pos = tkoff + start
    results: list[tuple[str, int]] = []
    seen: set[str] = set()

    while pos + 4 <= len(data):
        offset = (pos - tkoff) & 0xFFFF
        if deek(data, pos) == 0:
            break
        pos += 4  # skip instruction + function pointers

        # Read name bytes; last byte has its high bit set
        name_start = pos
        while pos < len(data) and (data[pos] & 0x80) == 0:
            pos += 1
        if pos >= len(data):
            break
        pos += 1  # include the high-bit terminator
        name_bytes = data[name_start:pos]

        # Read type/param bytes until 0xFD, 0xFE, or 0xFF
        while pos < len(data) and (data[pos] & 0xFF) < 0xFD:
            pos += 1
        if pos >= len(data):
            break
        pos += 1  # skip terminator

        # Word-align
        if pos & 1:
            pos += 1

        name = decode_name(name_bytes)
        if name is None:
            name = last_name
        elif name.startswith('!'):
            last_name = name[1:]
            name = last_name
        # else: normal entry, do NOT update last_name

        if not name or not name.strip():
            continue

        normalized = name.strip().upper()
        if normalized and normalized not in seen:
            seen.add(normalized)
            results.append((normalized, offset))

    return results


def enrich(
    json_path: Path,
    bin_path: Path,
    slot: int,
    start: int,
    extra_bytes_map: dict[int, int] | None = None,
    verbose: bool = True,
) -> None:
    with open(bin_path, 'rb') as f:
        data = f.read()

    pairs = parse_extension(data, start)
    bin_by_name = {name: offset for (name, offset) in pairs}

    with open(json_path) as f:
        doc = json.load(f)

    definitions: list[dict] = doc['definitions']
    matched = 0
    unmatched_json = []

    for defn in definitions:
        key = defn['name'].upper()
        if key in bin_by_name:
            offset = bin_by_name[key]
            defn['offset'] = offset
            if extra_bytes_map and offset in extra_bytes_map:
                defn['extraBytes'] = extra_bytes_map[offset]
            matched += 1
        else:
            unmatched_json.append(defn['name'])

    unmatched_bin = [name for name in bin_by_name if name not in {d['name'].upper() for d in definitions}]

    if verbose:
        print(f'  Binary entries: {len(pairs)}, JSON definitions: {len(definitions)}')
        print(f'  Matched: {matched}')
        if unmatched_bin:
            print(f'  In binary, not in JSON: {unmatched_bin}')
        if unmatched_json:
            print(f'  In JSON, not in binary (no offset): {unmatched_json}')

    # Update/add extension metadata
    if 'extension' not in doc:
        doc['extension'] = {}
    doc['extension']['slot'] = slot
    doc['extension']['start'] = start

    # Reorder keys: extension first, then definitions
    ordered = {}
    if 'extension' in doc:
        ordered['extension'] = doc['extension']
    ordered['definitions'] = doc['definitions']

    with open(json_path, 'w') as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)
        f.write('\n')
    print(f'  Written: {json_path}')


def main() -> None:
    root = Path(__file__).parent.parent
    bins = root / 'reference/amostools/extensions'
    defs = root / 'reference/amiga-amos/src/main/resources/amos/definitions'

    configs = [
        ('core',    bins / '00base.bin',    0, -194, EXTRA_BYTES),
        ('music',   bins / '01music.bin',   1,    6, None),
        ('compact', bins / '02compact.bin', 2,    6, None),
        ('request', bins / '03request.bin', 3,    6, None),
        ('ioports', bins / '06ioports.bin', 6,    6, None),
    ]

    for (name, bin_path, slot, start, extra) in configs:
        print(f'\n=== {name} (slot={slot}, start={start}) ===')
        enrich(defs / f'{name}.json', bin_path, slot, start, extra)


if __name__ == '__main__':
    main()
