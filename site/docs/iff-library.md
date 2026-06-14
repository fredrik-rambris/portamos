---
layout: default
title: IFF library
parent: Developing with Portamos
nav_order: 1
---

# IFF library

Portamos includes a small IFF support library under the `dev.rambris.iff` package. It provides:

- **IFF ILBM** read/write via `javax.imageio` (Java's standard `ImageIO` API)
- **IFF 8SVX** read/write via `javax.sound.sampled` (Java's standard audio API)

Both providers are registered automatically through the Java SPI mechanism when portamos is on the classpath. No
explicit registration code is needed.

---

## IFF ILBM images

IFF ILBM is the native image format on the Commodore Amiga. Portamos reads and writes indexed-colour ILBM images (with
`CMAP` palette), including ByteRun1 compressed `BODY` data.

### Reading an ILBM file

Use the standard `ImageIO` API:

```java
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

BufferedImage image = ImageIO.read(Path.of("sprite.iff").toFile());
// image type is BufferedImage.TYPE_BYTE_INDEXED
```

### Writing an ILBM file

```java
import javax.imageio.ImageIO;
import javax.imageio.ImageWriter;
import javax.imageio.stream.FileImageOutputStream;
import dev.rambris.iff.imageio.IlbmWriteParam;

BufferedImage image = ...; // must be TYPE_BYTE_INDEXED

Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("ilbm");
ImageWriter writer = writers.next();

IlbmWriteParam param = (IlbmWriteParam) writer.getDefaultWriteParam();
// param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
// param.setCompressionType("ByteRun1");

try (var out = new FileImageOutputStream(Path.of("output.iff").toFile())) {
    writer.setOutput(out);
    writer.write(null, new IIOImage(image, null, null), param);
}
```

Supported format names for `ImageIO`: `"ilbm"`, `"iff"`.

### IlbmUtility

`IlbmUtility` provides convenience methods for working with Amiga screen geometry and palette data:

```java
import dev.rambris.iff.IlbmUtility;

// Convert a BufferedImage.TYPE_BYTE_INDEXED to an IlbmImage model
IlbmImage ilbm = IlbmUtility.fromBufferedImage(image);

// Convert back
BufferedImage result = IlbmUtility.toBufferedImage(ilbm);
```

---

## IFF 8SVX audio

IFF 8SVX is the native mono audio format on the Commodore Amiga. Portamos reads and writes 8SVX files as 8-bit signed
PCM streams via the Java Sound API.

### Reading an 8SVX file

```java
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioInputStream;

AudioInputStream audio = AudioSystem.getAudioInputStream(Path.of("sample.8svx").toFile());
// audio.getFormat() returns 8-bit signed PCM at the sample's native frequency
byte[] pcmData = audio.readAllBytes();
```

### Writing an 8SVX file

```java
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import dev.rambris.iff.audio.Svx8AudioFileTypes;

AudioFormat format = new AudioFormat(8363f, 8, 1, true, false);
byte[] pcmData = ...; // 8-bit signed PCM samples

AudioInputStream audio = new AudioInputStream(
    new ByteArrayInputStream(pcmData), format, pcmData.length);

AudioSystem.write(audio, Svx8AudioFileTypes.SVX8, Path.of("output.8svx").toFile());
```

The `Svx8AudioFileTypes.SVX8` constant identifies the 8SVX file type to the `AudioSystem`.

---

## Low-level IFF parsing

For custom IFF chunk handling, use `IffReader` and `IffWriter` directly:

```java
import dev.rambris.iff.IffReader;
import dev.rambris.iff.IffId;

byte[] raw = Files.readAllBytes(Path.of("custom.iff"));
IffReader reader = new IffReader(raw);

reader.read((id, data) -> {
    System.out.printf("Chunk %s: %d bytes%n", id, data.length);
    return true; // return false to stop
});
```

```java
import dev.rambris.iff.IffWriter;
import dev.rambris.iff.FourCC;

IffWriter writer = new IffWriter();
writer.beginGroup(FourCC.of("FORM"), FourCC.of("MYTP"));
writer.writeChunk(FourCC.of("DATA"), myPayload);
writer.endGroup();

byte[] iff = writer.toBytes();
```
