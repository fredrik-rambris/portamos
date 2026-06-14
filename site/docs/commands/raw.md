---
layout: default
title: raw
parent: Commands
nav_order: 5
---

# `raw` — Wrap raw bytes in a bank envelope

Wraps a raw binary file in an AmBk bank envelope with the specified type.

## Usage

```
portamos raw <input.bin> <output.Abk> --type <TYPE> [options]
```

## Options

`--type <TYPE>`
: Bank type. Required. Valid values: `WORK`, `DATA`, `MUSIC`, `SAMPLES`, `ASM`, `CODE`, `AMAL`, `MENU`, `TRACKER`,
`DATAS`.

`--chip`
: Mark the bank as chip RAM.

`--bank-number <n>`
: Set the bank number (default: 1).

## Examples

```bash
portamos raw payload.bin output.Abk --type WORK
portamos raw payload.bin output.Abk --type MUSIC --chip --bank-number 3
```
