---
layout: default
title: AMAL bank
parent: Banks
nav_order: 4
---

# AMAL bank

An AMAL bank holds **AMAL animation programs**, **movement recordings** (recorded X/Y position deltas for the `PLAY`
command), and an optional **environment program**.

## Commands

```bash
# Export to JSON + .amal program files + movement JSON files
portamos disasm Amal.Abk output/amal.json

# Reassemble
portamos asm output/amal.json Amal.Abk
```

## Output files

| File                    | Description                       |
|-------------------------|-----------------------------------|
| `amal.json`             | Bank metadata and file references |
| `amal-program000.amal`  | AMAL program source for slot 0    |
| `amal-movement000.json` | Movement recording for slot 0     |
| `amal-environment.amal` | Environment program (if present)  |

## JSON schema

```json
{
  "type": "Amal",
  "bankNumber": 1,
  "chipRam": false,
  "environment": "amal-environment.amal",
  "programCount": 3,
  "programs": [
    { "index": 0, "file": "amal-program000.amal" },
    { "index": 1, "file": "amal-program001.amal" }
  ],
  "movements": [
    {
      "index": 0,
      "name": "Walk",
      "file": "amal-movement000.json"
    }
  ]
}
```

## Field reference

### Top level

| Field          | Type           | Description                                             |
|----------------|----------------|---------------------------------------------------------|
| `type`         | string         | Always `"Amal"`                                         |
| `bankNumber`   | integer        | Bank slot (1-based)                                     |
| `chipRam`      | boolean        | `true` = chip RAM, `false` = fast RAM                   |
| `environment`  | string or null | Path to environment `.amal` file; `null` if not present |
| `programCount` | integer        | Total number of program slots (including empty ones)    |

### `programs[]`

| Field   | Type    | Description                                      |
|---------|---------|--------------------------------------------------|
| `index` | integer | Program slot number                              |
| `file`  | string  | Path to `.amal` source file relative to the JSON |

### `movements[]`

| Field   | Type    | Description                                             |
|---------|---------|---------------------------------------------------------|
| `index` | integer | Movement slot number                                    |
| `name`  | string  | Movement name                                           |
| `file`  | string  | Path to movement `.json` file relative to the bank JSON |

### Movement file schema

Each movement is stored in its own JSON file:

```json
{
  "name": "Walk",
  "xMove": {
    "speed": 1,
    "instructions": [
      { "type": "Delta", "pixels": 2 },
      { "type": "Wait", "ticks": 3 },
      { "type": "Delta", "pixels": -2 }
    ]
  },
  "yMove": {
    "speed": 1,
    "instructions": []
  }
}
```

#### Instruction types

| `type`  | Fields                   | Description                                                                            |
|---------|--------------------------|----------------------------------------------------------------------------------------|
| `Delta` | `pixels` (integer)       | Move by this many pixels (positive = right/down, negative = left/up; range −63 to +63) |
| `Wait`  | `ticks` (integer, 1–127) | Pause for this many 1/50-second intervals                                              |
