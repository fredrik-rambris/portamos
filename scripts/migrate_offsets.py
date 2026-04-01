#!/usr/bin/env python3
"""
Migrates AMOS JSON definitions from the old schema (offset at outer level)
to the new schema (offset inside each signature).

commaGroups is derived directly from each entry's binary parameter string,
eliminating the need for manual OVERRIDES in most cases.

Run from the portamos project root:
    python3 scripts/migrate_offsets.py
"""
import json
import struct
import sys
from collections import defaultdict
from pathlib import Path

# ---------------------------------------------------------------------------
# Binary parsing
# ---------------------------------------------------------------------------

def leek(d: bytes, o: int) -> int:
    return struct.unpack_from('>I', d, o)[0]

def deek(d: bytes, o: int) -> int:
    return struct.unpack_from('>H', d, o)[0]

def decode_name(raw: bytes) -> str | None:
    if not raw:
        return None
    if raw[0] & 0xFF == 0x80:
        return None
    sb = []
    cap = True
    for b in raw:
        c = chr(b & 0x7F)
        if cap and c.islower():
            c = c.upper()
        cap = (c == ' ')
        sb.append(c)
    return ''.join(sb).rstrip(' ')


def cg_from_params(params: str) -> int:
    """
    Compute commaGroups from binary parameter string (e.g. 'I0,0t0').

    Format:
      first char = token type: I=instruction, 0=int-fn, 1=float-fn, 2=str-fn, V=variable
      remaining chars:
        0/1/2/3 = value parameter (integer/float/string/amal-string)
        t       = 'To' keyword separator
        ,       = comma separator between parameters (visual only)

    commaGroups = value_params - t_keywords
    This matches AsciiParser.countCommaGroups() which returns commas+1 at depth 0.
    """
    if not params:
        return 0
    values = sum(1 for c in params[1:] if c in '0123')
    keywords = sum(1 for c in params[1:] if c == 't')
    return max(values - keywords, 0)


def parse_all_forms(data: bytes, start: int) -> list[tuple[str, int, int]]:
    """
    Returns ALL (normalized_uppercase_name, offset, commaGroups) triples,
    including duplicate names for multi-form keywords.
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
    if leek(data, 32 + 18) == 0x41503230:
        tkoff += 4

    last_name: str | None = None
    pos = tkoff + start
    results: list[tuple[str, int, int]] = []

    while pos + 4 <= len(data):
        offset = (pos - tkoff) & 0xFFFF
        if deek(data, pos) == 0:
            break
        pos += 4

        name_start = pos
        while pos < len(data) and (data[pos] & 0x80) == 0:
            pos += 1
        if pos >= len(data):
            break
        pos += 1
        name_bytes = data[name_start:pos]

        param_start = pos
        while pos < len(data) and (data[pos] & 0xFF) < 0xFD:
            pos += 1
        if pos >= len(data):
            break
        params_str = data[param_start:pos].decode('latin-1', errors='replace')
        pos += 1
        if pos & 1:
            pos += 1

        name = decode_name(name_bytes)
        if name is None:
            name = last_name
        elif name.startswith('!'):
            last_name = name[1:]
            name = last_name

        if name and name.strip():
            cg = cg_from_params(params_str)
            results.append((name.strip().upper(), offset, cg))

    return results


# ---------------------------------------------------------------------------
# commaGroups from documented JSON signature (used to match against binary cg)
# ---------------------------------------------------------------------------

def sig_comma_groups(sig: dict) -> int:
    """commaGroups from a JSON signature's parameter list."""
    params = sig.get('parameters', [])
    v = sum(1 for p in params if p.get('kind') == 'value')
    k = sum(1 for p in params if p.get('kind') == 'keyword')
    return max(v - k, 0)


def make_skeleton_sig(offset: int, cg: int) -> dict:
    """Skeleton signature for a binary form with no documentation."""
    return {"offset": offset, "commaGroups": cg, "parameters": []}


# ---------------------------------------------------------------------------
# Manual overrides for keywords that cannot be resolved by commaGroups alone
# (e.g. two forms with identical argument count but different behaviour).
# Add entries here only when tests confirm auto-assignment is wrong.
# ---------------------------------------------------------------------------

OVERRIDES: dict[str, list[dict]] = {
    # Example (not currently needed):
    # "SOME KEYWORD": [
    #     {"offset": 1234, "presentation": "...", "parameters": [...]},
    #     {"offset": 1250, "presentation": "...", "parameters": [...]},
    # ],
}


# ---------------------------------------------------------------------------
# Core migration logic
# ---------------------------------------------------------------------------

def migrate_definition(defn: dict, all_forms: list[tuple[int, int]]) -> dict:
    """
    Assigns per-signature offsets derived from the binary.

    all_forms is a list of (offset, commaGroups) pairs for this keyword,
    sorted ascending by commaGroups.  Each pair is matched against the
    documented JSON signatures by commaGroups value; unmatched binary forms
    get skeleton signatures appended.

    If all_forms is empty (no binary match), falls back to the outer 'offset'
    field from the old schema for single-form keywords.
    """
    name = defn.get("name", "").strip().upper()
    outer_offset = defn.pop("offset", None)
    defn.pop("altOffset", None)   # no longer used

    sigs: list[dict] = defn.setdefault("signatures", [])

    # ---- Apply manual override if available --------------------------------
    if name in OVERRIDES:
        defn["signatures"] = [dict(s) for s in OVERRIDES[name]]
        return defn

    # ---- No binary data: fall back to outer_offset -------------------------
    if not all_forms:
        if outer_offset is not None:
            unassigned = [s for s in sigs if 'offset' not in s]
            if unassigned:
                unassigned[0]['offset'] = outer_offset
            else:
                sigs.append(make_skeleton_sig(outer_offset, sig_comma_groups(sigs[0]) if sigs else 0))
        return defn

    # ---- cg-based matching -------------------------------------------------
    # Build map: commaGroups -> list of doc sigs with that cg
    sigs_by_cg: dict[int, list[dict]] = {}
    for sig in sigs:
        cg = sig_comma_groups(sig)
        sigs_by_cg.setdefault(cg, []).append(sig)

    sorted_forms = sorted(all_forms, key=lambda x: x[1])
    unmatched: list[tuple[int, int]] = []

    for (offset, cg) in sorted_forms:
        candidates = sigs_by_cg.get(cg, [])
        unassigned = [s for s in candidates if 'offset' not in s]
        if unassigned:
            unassigned[0]['offset'] = offset
        else:
            unmatched.append((offset, cg))

    # ---- Positional fallback for forms not matched by binary cg ------------
    # Handles keywords where the binary param string says 'I' (cg=0) but the
    # documented signature has a higher cg (e.g. ADD: params='I' but doc cg=2).
    # Assign leftover binary forms to remaining unassigned doc sigs in order.
    remaining_sigs = [s for s in sigs if 'offset' not in s]
    for i, (offset, binary_cg) in enumerate(unmatched):
        if i < len(remaining_sigs):
            remaining_sigs[i]['offset'] = offset
        else:
            # No doc sig to assign to — create a skeleton with a unique cg
            used_cgs = {s.get('commaGroups', sig_comma_groups(s)) for s in sigs}
            skeleton_cg = binary_cg
            while skeleton_cg in used_cgs:
                skeleton_cg += 1
            sigs.append(make_skeleton_sig(offset, skeleton_cg))

    return defn


def migrate_file(json_path: Path, bin_path: Path, start: int,
                 out_path: Path | None = None) -> None:
    if out_path is None:
        out_path = json_path
    print(f"Migrating {json_path.name} → {out_path} ...")
    with open(json_path) as f:
        doc = json.load(f)

    # Build name → [(offset, cg)] from binary
    with open(bin_path, "rb") as f:
        bin_data = f.read()
    all_entries = parse_all_forms(bin_data, start)
    by_name: dict[str, list[tuple[int, int]]] = defaultdict(list)
    for (name, offset, cg) in all_entries:
        by_name[name].append((offset, cg))

    definitions: list[dict] = doc["definitions"]
    migrated = 0
    for defn in definitions:
        name_upper = defn.get("name", "").strip().upper()
        forms = by_name.get(name_upper, [])
        migrate_definition(defn, forms)
        migrated += 1

    print(f"  Processed {migrated} definitions.")

    ordered: dict = {}
    if "extension" in doc:
        ordered["extension"] = doc["extension"]
    ordered["definitions"] = definitions

    with open(out_path, "w") as f:
        json.dump(ordered, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"  Written: {out_path}")


def main() -> None:
    root = Path(__file__).parent.parent
    bins = root / "reference/amostools/extensions"
    src_defs = root / "reference/amiga-amos/src/main/resources/amos/definitions"
    dst_defs = root / "src/main/resources/amos/definitions"

    configs = [
        ("core",    bins / "00base.bin",    -194),
        ("music",   bins / "01music.bin",      6),
        ("compact", bins / "02compact.bin",    6),
        ("request", bins / "03request.bin",    6),
        ("ioports", bins / "06ioports.bin",    6),
    ]

    for (name, bin_path, start) in configs:
        src_path = src_defs / f"{name}.json"
        dst_path = dst_defs / f"{name}.json"
        migrate_file(src_path, bin_path, start, dst_path)


if __name__ == "__main__":
    main()
