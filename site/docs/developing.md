---
layout: default
title: Developing with Portamos
nav_order: 6
has_children: true
---

# Developing with Portamos

Portamos is published to [GitHub Packages](https://github.com/fredrik-rambris/portamos/packages) and can be used as a
library in your own Java projects. The artifact requires **Java 21** or later.

## Dependency

### Gradle (Kotlin DSL)

```kotlin
repositories {
    maven {
        url = uri("https://maven.pkg.github.com/fredrik-rambris/portamos")
        credentials {
            username = providers.gradleProperty("gpr.user").orNull ?: System.getenv("GITHUB_ACTOR")
            password = providers.gradleProperty("gpr.key").orNull ?: System.getenv("GITHUB_TOKEN")
        }
    }
}

dependencies {
    implementation("dev.rambris:portamos:{{ site.release_tag | remove_first: 'v' }}")
}
```

### Maven

```xml

<repositories>
    <repository>
        <id>github-portamos</id>
        <url>https://maven.pkg.github.com/fredrik-rambris/portamos</url>
    </repository>
</repositories>

<dependency>
<groupId>dev.rambris</groupId>
<artifactId>portamos</artifactId>
<version>{{ site.release_tag | remove_first: 'v' }}</version>
</dependency>
```

GitHub Packages requires authentication even for public packages. Add your GitHub username and
a [personal access token](https://github.com/settings/tokens) (with `read:packages` scope) to `~/.m2/settings.xml` or
Gradle's `gradle.properties`.

---

## Working with banks

### Reading a bank

`AmosBank.read(Path)` dispatches automatically to the correct reader based on the bank magic and name header:

```java
import dev.rambris.amigaamos.bank.AmosBank;
import dev.rambris.amigaamos.bank.SampleBank;

AmosBank bank = AmosBank.read(Path.of("Samples.Abk"));

if(bank instanceof
SampleBank samples){
        for(
var sample :samples.

samples()){
        System.out.

printf("%s  %d Hz  %d bytes%n",
       sample.name(),sample.

frequencyHz(),sample.

data().length);
        }
        }
```

### Writing a bank

Each bank type has a corresponding writer. Retrieve it from the bank itself via `bank.writer()`:

```java
import dev.rambris.amigaamos.bank.AmosBank;
import dev.rambris.amigaamos.bank.SampleBank;

AmosBank bank = AmosBank.read(Path.of("Samples.Abk"));
// ... modify the bank model ...
bank.

writer().

write(bank, Path.of("Samples-modified.Abk"));
```

Or get the bytes directly (useful when embedding banks in a `.AMOS` file):

```java
byte[] bankBytes = bank.writer().toBytes(bank);
```

---

## Working with AMOS program files

### Detokenizing a binary to ASCII

```java
import dev.rambris.amigaamos.tokenizer.Tokenizer;
import dev.rambris.amigaamos.tokenizer.model.AmosFile;

Tokenizer tokenizer = new Tokenizer();
AmosFile program = tokenizer.decode(Path.of("Program.AMOS"));

// Print as ASCII source
List<String> lines = tokenizer.print(program);
lines.

forEach(System.out::println);

// Or write directly to a file
tokenizer.

print(program, Path.of("Program.Asc"));
```

### Tokenizing ASCII source to binary

```java
import dev.rambris.amigaamos.tokenizer.Tokenizer;
import dev.rambris.amigaamos.tokenizer.model.AmosFile;

Tokenizer tokenizer = new Tokenizer();
AmosFile program = tokenizer.parse(Path.of("Program.Asc"));

byte[] binary = tokenizer.encode(program);
Files.

write(Path.of("Program.AMOS"),binary);
```

### Using third-party extension definitions

```java
Tokenizer tokenizer = new Tokenizer()
        .withoutDefinition("music")                      // drop built-in Music extension
        .withDefinition(Path.of("definitions/amcaf.json")); // load AMCAF instead

AmosFile program = tokenizer.decode(Path.of("Program.AMOS"));
```

---

## IFF support

Adding portamos to your project also registers IFF ILBM and IFF 8SVX providers into the standard Java `ImageIO` and
`javax.sound.sampled` APIs automatically via the Java SPI mechanism — no extra configuration required.

See the [IFF library]({% link docs/iff-library.md %}) page for details.
