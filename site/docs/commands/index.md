---
layout: default
title: Commands
nav_order: 3
has_children: true
---

# Commands

Portamos is a command-line tool. Run `portamos --help` for a summary, or `portamos <command> --help` for options
specific to a command.

| Command                                        | Description                                            |
|------------------------------------------------|--------------------------------------------------------|
| [`list`]({% link docs/commands/list.md %})     | Detokenize a binary `.AMOS` file to ASCII source       |
| [`build`]({% link docs/commands/build.md %})   | Tokenize an ASCII source file to a binary `.AMOS` file |
| [`disasm`]({% link docs/commands/disasm.md %}) | Export a bank (`.Abk` / `.Abs`) to JSON + data files   |
| [`asm`]({% link docs/commands/asm.md %})       | Assemble a bank from a JSON metadata file              |
| [`raw`]({% link docs/commands/raw.md %})       | Wrap raw bytes in a bank envelope                      |
| [`concat`]({% link docs/commands/concat.md %}) | Bundle multiple `.Abk` files into a `.Abs` bank set    |
