---
layout: default
title: Music bank
parent: Banks
nav_order: 7
---

# Music bank

The AMOS Music bank holds three independent sections: **instruments** (sample data), **songs** (per-channel pattern
playlists), and **patterns** (per-voice note and command streams). Unlike ProTracker, the four voices are fully
independent — there is no shared row grid.

## Commands

```bash
# Export to JSON + WAV instrument samples
portamos disasm Music.Abk output/music.json

# Export with IFF 8SVX instrument samples
portamos disasm --8svx Music.Abk output/music.json

# Reassemble
portamos asm output/music.json Music.Abk
```

## JSON schema

```json
{
  "type": "Music",
  "bankNumber": 3,
  "chipRam": true,
  "instruments": [
    {
      "name": "Bass",
      "volume": 45,
      "loopStart": 112,
      "loopLength": 3247,
      "sample": "music-instrument000.wav"
    }
  ],
  "songs": [
    {
      "name": "My Song",
      "tempo": 15,
      "sequence": [
        [0, 1, 2, 65534],
        [0, 1, 2, 65534],
        [0, 1, 2, 65534],
        [0, 1, 2, 65534]
      ]
    }
  ],
  "patterns": [
    {
      "voices": [
        [
          { "command": "SET_INSTR", "parameter": 0 },
          { "period": 254, "duration": 16134 },
          { "period": 190, "duration": 16134 }
        ],
        [],
        [],
        []
      ]
    }
  ]
}
```

## Field reference

### Top level

| Field        | Type    | Description                           |
|--------------|---------|---------------------------------------|
| `type`       | string  | Always `"Music"`                      |
| `bankNumber` | integer | Bank slot (1-based)                   |
| `chipRam`    | boolean | `true` = chip RAM, `false` = fast RAM |

### `instruments[]`

| Field        | Type    | Description                                                        |
|--------------|---------|--------------------------------------------------------------------|
| `name`       | string  | Up to 16 ASCII characters                                          |
| `volume`     | integer | Default playback volume (0–63)                                     |
| `loopStart`  | integer | Byte offset within sample data where the loop starts (0 = no loop) |
| `loopLength` | integer | Loop length in 16-bit words (2 = no loop)                          |
| `sample`     | string  | Path to audio file relative to the JSON                            |

Instrument samples are exported at their stored frequency (8363 Hz = Amiga standard). Actual pitch at runtime is
determined by the note periods in the patterns.

### `songs[].sequence`

Four per-channel playlists. Each is a list of 0-based pattern indices, terminated by:

- `65534` (`0xFFFE`) — loop back to the start of this channel's playlist
- `65535` (`0xFFFF`) — stop playback on this channel

### `patterns[].voices`

Four independent note streams. Each item is either:

**Note:**

```json
{ "period": 254, "duration": 16134 }
```

| Field      | Type    | Description                                                                                          |
|------------|---------|------------------------------------------------------------------------------------------------------|
| `period`   | integer | Amiga hardware period (inversely proportional to pitch; 0 = silence)                                 |
| `duration` | integer | Raw OldNote control word (bit 14 cleared); bits 13–8 = per-note volume (0–63), bits 7–0 = tick count |

**Command:**

```json
{ "command": "SET_VOLUME", "parameter": 48 }
```

| Field       | Type    | Description                                |
|-------------|---------|--------------------------------------------|
| `command`   | string  | Command name (see list below)              |
| `parameter` | integer | Command parameter (optional; 0 if omitted) |

### Command names

`SET_VOLUME`, `STOP_EFFECT`, `REPEAT`, `FILTER_ON`, `FILTER_OFF`, `SET_TEMPO`, `SET_INSTR`, `ARPEGGIO`,
`TONE_PORTAMENTO`, `VIBRATO`, `VOLUME_SLIDE`, `PORTAMENTO_UP`, `PORTAMENTO_DOWN`, `DELAY`, `POSITION_JUMP`
