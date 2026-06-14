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

| Platform                             | File                                                                                                                                          |
|--------------------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------|
| Linux (x86-64)                       | [portamos-linux-amd64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-linux-amd64)             |
| macOS (ARM / Apple Silicon)          | [portamos-macos-arm64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-macos-arm64)             |
| macOS (Intel)                        | [portamos-macos-amd64](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-macos-amd64)             |
| Windows (x86-64)                     | [portamos-windows-amd64.exe](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-windows-amd64.exe) |
| Fat JAR (any platform with Java 21+) | [portamos-all.jar](https://github.com/fredrik-rambris/portamos/releases/download/{{ site.release_tag }}/portamos-all.jar)                     |

The native binaries are self-contained — no JVM installation is required. The fat JAR requires Java 21 or later; run it
with `java -jar portamos-all.jar`.

## Extension definitions (optional)

The release archive also contains a `definitions/` directory with JSON definition files for known third-party AMOS
extensions (e.g. `turboplus.json`). If you work with programs that use third-party extensions, place the `definitions/`
folder alongside the binary.

## Installation

The simplest approach is to place the binary (and optionally the `definitions/` folder) in a dedicated directory and add
that directory to your `PATH`.

### Linux and macOS

```bash
mkdir -p ~/portamos
# Move the downloaded binary here and make it executable
mv portamos-linux-amd64 ~/portamos/portamos      # adjust filename for your platform
chmod +x ~/portamos/portamos
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
2. Move `portamos-windows-amd64.exe` into that folder and rename it to `portamos.exe`.
3. Open **System Properties** → **Advanced** → **Environment Variables**.
4. Under **User variables**, select **Path** and click **Edit**.
5. Click **New** and add `C:\portamos`.
6. Click **OK** to close all dialogs.
7. Open a new Command Prompt or PowerShell window and verify:

```powershell
portamos --version
```
