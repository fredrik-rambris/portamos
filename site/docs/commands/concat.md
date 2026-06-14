---
layout: default
title: concat
parent: Commands
nav_order: 6
---

# `concat` — Bundle banks into a bank set

Bundles one or more `.Abk` bank files into a single `.Abs` bank set file.

## Usage

```
portamos concat <bank1.Abk> [<bank2.Abk> ...] <output.Abs>
```

## Example

```bash
portamos concat sprites.Abk music.Abk samples.Abk AllBanks.Abs
```
