---
layout: default
title: Installing
nav_order: 2
---

# Installing Portamos

## Download

The latest release is **[{{ site.release_tag }}](https://github.com/fredrik-rambris/portamos/releases/tag/{{
site.release_tag }})**.

Download the binary for your platform:

| Platform                             | File                                                                                                                                                                 |
|--------------------------------------|----------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Linux (x86-64)                       | [portamos-linux-amd64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-{{ site.release_tag }}-linux-amd64.tar.gz)      |
| macOS (ARM / Apple Silicon)          | [portamos-macos-aarch64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-{{ site.release_tag }}-macos-aarch64.tar.gz)  |
| Windows (x86-64)                     | [portamos-windows-amd64.exe](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-{{ site.release_tag }}-windows-amd64.zip) |
| Fat JAR (any platform with Java 21+) | [portamos-all.jar](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-all.jar)                                            |

The native binaries are self-contained — no JVM installation is required. The fat JAR requires Java 21 or later; run it
with `java -jar portamos-all.jar`.

## Archive contents

Each release archive contains:

```
portamos          (or portamos.exe on Windows)
README.md
definitions/
  README.md
  turboplus.json
  amcaf.json
```

The `definitions/` directory contains JSON definition files for known third-party AMOS extensions. Place it alongside
the binary so Portamos can find them with `--definition definitions/turboplus.json`.

## Installation

### Linux and macOS

Extract the archive and place the contents in a dedicated directory:

```bash
mkdir -p ~/portamos
tar -xzf portamos-*-linux-amd64.tar.gz -C ~/portamos   # adjust filename for your platform
```

Add the directory to your `PATH` by appending this line to `~/.bashrc`, `~/.zshrc`, or your shell's equivalent:

```bash
export PATH="$HOME/portamos:$PATH"
```

Reload your shell or run `source ~/.bashrc` (or `~/.zshrc`), then verify:

```bash
portamos --version
```

### Windows

1. Create a folder, for example `C:\portamos`.
2. Extract `portamos-*-windows-amd64.zip` into that folder.
3. Open **System Properties** → **Advanced** → **Environment Variables**.
4. Under **User variables**, select **Path** and click **Edit**.
5. Click **New** and add `C:\portamos`.
6. Click **OK** to close all dialogs.
7. Open a new Command Prompt or PowerShell window and verify:

```powershell
portamos --version
```
