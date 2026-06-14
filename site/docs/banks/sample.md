---
layout: default
title: Sample bank
parent: Banks
nav_order: 2
---

# Sample bank

A Sample bank holds a table of 8-bit signed mono PCM samples, each with an 8-character ASCII name and a playback
frequency in Hz. Used by AMOS with the `SAMPLE PLAY` command.

Samples are always stored in chip RAM.

## Commands

```bash
# Export to JSON + WAV files
portamos disasm Samples.Abk output/samples.json

# Export with IFF 8SVX audio instead of WAV
portamos disasm --8svx Samples.Abk output/samples.json

# Name audio files after sample names rather than index
portamos disasm --sample-names Samples.Abk output/samples.json

# Reassemble
portamos asm output/samples.json Samples.Abk
```

## JSON schema

```json5
{
  "type": "Samples",
  "bankNumber": 5,
  "chipRam": true,
  "samples": [
    {
      "name": "BanjoSyn",
      // playbackRate omitted — read from the audio file's sample rate
      "file": "samples-sample000.wav"
    },
    {
      "name": "LAAA",
      "playbackRate": 14563, // override when the audio file's rate doesn't match
      "file": "samples-sample003.wav"
    },
    {
      "name": "Empty"
      // no "file" key — empty slot
    }
  ]
}
```

| Field                    | Type    | Description                                                                                                                   |
|--------------------------|---------|-------------------------------------------------------------------------------------------------------------------------------|
| `type`                   | string  | Always `"Samples"`                                                                                                            |
| `bankNumber`             | integer | Bank slot (1-based)                                                                                                           |
| `chipRam`                | boolean | Always `true` for sample banks                                                                                                |
| `samples[].name`         | string  | Up to 8 ASCII characters                                                                                                      |
| `samples[].playbackRate` | integer | The rate at which AMOS plays back the sample, in Hz. If omitted or set to 0, read from the audio file's sample rate metadata. |
| `samples[].file`         | string  | Path to audio file relative to the JSON; omit for empty slots                                                                 |

## Audio files

Samples are exported as **RIFF WAV** (8-bit unsigned PCM) by default, or as **IFF 8SVX** (8-bit signed PCM) with
`--8svx`. On import, both WAV and 8SVX are accepted and converted automatically.

{: .note }
WAV 8-bit audio is unsigned. Portamos converts to/from the signed format AMOS uses automatically — you do not need to
account for this when editing samples in an audio editor.
