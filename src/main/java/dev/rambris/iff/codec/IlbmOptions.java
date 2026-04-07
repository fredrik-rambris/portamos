package dev.rambris.iff.codec;

/**
 * Write-time options for {@link IlbmCodec#write(IlbmImage, IlbmOptions...)}.
 */
public enum IlbmOptions {

    /**
     * Compress the {@code BODY} chunk with the ByteRun1 (PackBits) algorithm.
     * Without this option the body is written uncompressed.
     */
    COMPRESSION_BYTERUN1
}
