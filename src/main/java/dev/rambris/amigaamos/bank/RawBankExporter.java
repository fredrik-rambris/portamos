package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exports a {@link RawBank} to a data file and a sidecar
 * JSON metadata file.
 *
 * <p>Usage: call {@link #export(AmosBank, Path)} with the desired path for the
 * raw data content.  The metadata JSON is written to {@code dataPath + ".json"}.
 *
 * <p>Example — given {@code dataPath = "path/to/MyData.dat"}:
 * <pre>
 *   path/to/MyData.dat       ← raw payload bytes (no AmBk header)
 *   path/to/MyData.dat.json  ← metadata
 * </pre>
 *
 * <p>JSON format:
 * <pre>
 * {
 *   "type":       "WORK" | "DATA",
 *   "bankNumber": 10,
 *   "chipRam":    false,
 *   "dataFile":   "MyData.dat"   ← filename only (no directory component)
 * }
 * </pre>
 *
 * <p>The {@code dataFile} value is the file name only (no directory path), so that
 * the exported bundle is relocatable.  {@link RawBankImporter} resolves it relative
 * to the JSON file's parent directory.
 */
public class RawBankExporter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Exports {@code bank} to {@code dataPath} (raw payload) and {@code dataPath + ".json"} (metadata).
     *
     * @param bank     the Work or Data bank to export
     * @param dataPath destination path for the raw payload bytes
     * @throws IllegalArgumentException if {@code bank} is not a {@link RawBank}
     */
    public void export(AmosBank bank, Path dataPath) throws IOException {
        if (!(bank instanceof RawBank rb))
            throw new IllegalArgumentException("Not a Work or Data bank: " + bank.type());
        byte[] payload = rb.data();

        Files.createDirectories(dataPath.toAbsolutePath().getParent());
        Files.write(dataPath, payload);

        ObjectNode root = JSON.createObjectNode();
        root.put("type",       bank.type().name());
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam",    bank.chipRam());
        root.put("dataFile",   dataPath.getFileName().toString());

        Path jsonPath = dataPath.resolveSibling(dataPath.getFileName() + ".json");
        JSON.writeValue(jsonPath.toFile(), root);
    }
}
