---
layout: default
title: Tracker bank
parent: Banks
nav_order: 5
---

# Tracker bank

A Tracker bank wraps a standard **ProTracker MOD** file inside an AMOS bank envelope.

## Commands

```bash
# Export MOD file from bank
portamos disasm Song.Abk output/song.json

# Reassemble bank from MOD file
portamos asm output/song.json Song.Abk
```

## JSON schema

```json
{
  "type": "Tracker",
  "bankNumber": 1,
  "chipRam": false,
  "modFile": "song.mod"
}
```

| Field        | Type    | Description                                  |
|--------------|---------|----------------------------------------------|
| `type`       | string  | Always `"Tracker"`                           |
| `bankNumber` | integer | Bank slot (1-based)                          |
| `chipRam`    | boolean | `true` = chip RAM, `false` = fast RAM        |
| `modFile`    | string  | Path to the `.mod` file relative to the JSON |
