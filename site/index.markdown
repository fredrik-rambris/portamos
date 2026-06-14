---
layout: home
title: Home
nav_order: 1
---

# Portamos

**Portamos** is a portable, CLI based toolkit for working
with [AMOS Professional](https://en.wikipedia.org/wiki/AMOS_(programming_language)) files on modern systems. The name is
a play on *portable AMOS*.

It is bidirectional as you can both export and import to and from these now old formats. You instead work with
JSON, text, PNG and WAVE files and use `portamos` to convert them into Amiga formats, finally copying them over
to real or emulated Amiga computers. When paired with an IDE like [IntelliJ IDEA](https://www.jetbrains.com/idea/)
and a simple `Makefile` to build all assets it becomes a quite nice developer experience.

AMOS Professional is a BASIC-like programming language for the Commodore Amiga, developed by François Lionet. Programs
are stored as tokenised binary files (`.AMOS`), and multimedia data is stored in memory bank files (`.Abk`).

You can also use it as a library for your Java programs and build your own tools for working with AMOS files.

It also adds support for working with IFF ILBM and IFF 8SVX files in standard Java ImageIO and sound APIs.

---

## Features

- Listing binary (`.AMOS`) files and save them to ASCII source (`.Asc`)
- Tokenising ASCII source (`.Asc`) into binary (`.AMOS`) files, allowing you to edit in favorite IDE
- All instructions, functions and keywords supported by AMOS Professional v2.00 is supported. Definitions for
  extensions can be loaded at runtime. They can be generated from the extension `.Lib` file. They can optionally
  be enriched with documentation and can then be used with
  my [IntelliJ plugin IntelliJ-AMOS](https://github.com/fredrik-rambris/intellij-amos)
- Compiled procedures are inlined as Base64 encoded binary
- Adding bank (`.Abk`) files into `.AMOS` files
- All official bank types can be exported to JSON and accompanying files in modern formats
    - Sprites/Icons (JSON + sprite sheet `.png` or `.ilbm`)
    - Sample (JSON + `.wav` or `.8svx` files)
    - AMAL (JSON + programs > `.Amal`)
    - Resource (JSON + sprite sheet `.png` or `.ilbm` + programs > `.Amui`)
    - Pac.Pic (JSON + `.png` or `.ilbm`)
    - Music (JSON + `.wav` or `.8svx` files)
    - Tracker (JSON + `.mod` files)
    - Work and Data (JSON + raw files)
    - Menu (JSON)
    - Bank sets (`.Abs`) exports into `.Abk` files
- Exported banks and bank sets can be reassembled into `.Abk` and `.Abs` files allowing for modern workflows
- Palette optimiser for Pac.Pic, allowing for even smaller files

---

## Download

The latest release is **[{{ site.release_tag }}](https://github.com/fredrik-rambris/portamos/releases/tag/{{
site.release_tag }})**.

| Platform                | Download                                                                                                                                      |
|-------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Linux (x86-64)          | [portamos-linux-amd64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-linux-amd64)             |
| macOS (ARM)             | [portamos-macos-arm64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-macos-arm64)             |
| macOS (Intel)           | [portamos-macos-amd64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-macos-amd64)             |
| Windows (x86-64)        | [portamos-windows-amd64.exe](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-windows-amd64.exe) |
| Fat JAR (all platforms) | [portamos-all.jar](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-all.jar)                     |

See the [Installing]({% link docs/installing.md %}) page for setup instructions.

---

## Recent Changes

See [Releases](https://github.com/fredrik-rambris/portamos/releases/) page for full history

{% for release in site.data.changelog %}

### {{ release.tag }}

{% for change in release.changes %}- {{ change }}
{% endfor %}
{% endfor %}
