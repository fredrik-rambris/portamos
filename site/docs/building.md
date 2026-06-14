---
layout: default
title: Building from source
nav_order: 5
---

# Building from source

## Requirements

- **Java 21** — tested with [Eclipse Temurin 21](https://adoptium.net/). The version is pinned in `.sdkmanrc`; if you
  use [SDKMAN!](https://sdkman.io/) run `sdk env` in the project root.
- The Gradle wrapper (`./gradlew` / `gradlew.bat`) downloads Gradle automatically — no separate Gradle installation is
  required.

## Building

```bash
git clone https://github.com/fredrik-rambris/portamos.git
cd portamos
./gradlew build
```

This runs all tests and produces a fat JAR at:

```
build/libs/portamos-<version>-all.jar
```

Run it directly:

```bash
java -jar build/libs/portamos-*-all.jar --help
```

## Running without installing

You can invoke the tool via Gradle during development:

```bash
./gradlew run --args="list program.AMOS program.Asc"
```

## Native binaries

The release workflow builds native executables with GraalVM Native Image. To reproduce a native build locally you need
GraalVM 21 with the `native-image` component installed:

```bash
./gradlew nativeCompile
```

The binary is written to `build/native/nativeCompile/portamos`.

## Running the tests

```bash
./gradlew test
```

Integration tests round-trip several reference `.AMOS` / `.Asc` pairs and verify byte-identical output.
