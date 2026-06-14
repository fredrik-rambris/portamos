# Portamos

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

A portable, CLI-based toolkit for working
with [AMOS Professional](https://en.wikipedia.org/wiki/AMOS_(programming_language)) files on modern systems. The name is
a play on *portable AMOS*.

## Download

Downloads for Linux, Mac and Windows as well as fat jar
in [Releases](https://github.com/fredrik-rambris/portamos/releases/latest)

## Documentation

See [portamos.rambris.dev](https://portamos.rambris.dev/)

## Build requirements

- Java 21 (tested with [Eclipse Temurin 21](https://adoptium.net/))

The Gradle wrapper (`./gradlew`) downloads Gradle automatically; no separate installation is needed.

## Building

```bash
./gradlew build
```

This produces a fat JAR at `build/libs/portamos-<version>-all.jar`.

You can also run directly via Gradle:

```bash
./gradlew run --args="<subcommand> [options]"
```
