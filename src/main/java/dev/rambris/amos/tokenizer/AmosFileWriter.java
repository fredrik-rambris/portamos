package dev.rambris.amos.tokenizer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Writes the complete AMOS binary file format.
 *
 * File structure:
 *   Bytes  0-15: 16-byte version header
 *   Bytes 16-19: uint32 big-endian code length (n)
 *   Bytes 20 to 20+n-1: tokenized code (sequence of encoded lines)
 *   Bytes 20+n onwards: "AmBs" + uint16 bank count (6 bytes minimum for zero banks)
 */
public class AmosFileWriter {

    /**
     * Writes all encoded lines into a complete AMOS binary file.
     *
     * @param version      the AMOS version to use for the file header
     * @param encodedLines the list of already-encoded lines (from BinaryEncoder.encodeLine)
     * @return the complete binary file as a byte array
     */
    public byte[] write(AmosVersion version, List<byte[]> encodedLines) {
        int codeLen = encodedLines.stream().mapToInt(l -> l.length).sum();

        ByteArrayOutputStream out = new ByteArrayOutputStream(22 + codeLen + 6);

        // 16-byte version header
        byte[] header = version.headerBytes();
        try {
            out.write(header);

            // Code length: 4 bytes big-endian
            out.write((codeLen >> 24) & 0xFF);
            out.write((codeLen >> 16) & 0xFF);
            out.write((codeLen >> 8) & 0xFF);
            out.write(codeLen & 0xFF);

            // Code section: all encoded lines
            for (byte[] line : encodedLines) {
                out.write(line);
            }

            // AmBs section: magic + uint16 bank count = 0
            out.write('A');
            out.write('m');
            out.write('B');
            out.write('s');
            out.write(0x00); // bank count high byte
            out.write(0x00); // bank count low byte
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new RuntimeException("Unexpected IO error", e);
        }

        return out.toByteArray();
    }
}
