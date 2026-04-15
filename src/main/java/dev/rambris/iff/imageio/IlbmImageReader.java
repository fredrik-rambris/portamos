/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.imageio;

import dev.rambris.iff.IlbmUtility;
import dev.rambris.iff.codec.IlbmCodec;
import dev.rambris.iff.codec.IlbmImage;

import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.Iterator;

/**
 * ImageIO reader for IFF ILBM files.
 *
 * <p>Decodes indexed-colour ILBM images (with {@code CMAP} palette) into
 * {@link BufferedImage#TYPE_BYTE_INDEXED} images. Supports both uncompressed
 * and ByteRun1-compressed {@code BODY} data.
 *
 * <p>Metadata is not supported; {@link #getStreamMetadata()} and
 * {@link #getImageMetadata(int)} both return {@code null}.
 */
public final class IlbmImageReader extends ImageReader {

    /** Lazily loaded raw file bytes. */
    private byte[] rawData;
    /** Lazily decoded IlbmImage. */
    private IlbmImage decoded;
    /** Lazily converted BufferedImage (cached to avoid double conversion). */
    private BufferedImage buffered;

    IlbmImageReader(IlbmImageReaderSpi originatingProvider) {
        super(originatingProvider);
    }

    // -------------------------------------------------------------------------
    // Input management
    // -------------------------------------------------------------------------

    @Override
    public void setInput(Object input, boolean seekForwardOnly, boolean ignoreMetadata) {
        super.setInput(input, seekForwardOnly, ignoreMetadata);
        rawData  = null;
        decoded  = null;
        buffered = null;
    }

    // -------------------------------------------------------------------------
    // Lazy loaders
    // -------------------------------------------------------------------------

    private byte[] loadRaw() throws IOException {
        if (rawData == null) {
            var stream = (ImageInputStream) input;
            stream.seek(0);
            long len = stream.length();
            if (len >= 0) {
                rawData = new byte[(int) len];
                stream.readFully(rawData);
            } else {
                var baos = new ByteArrayOutputStream();
                var buf = new byte[8192];
                int n;
                while ((n = stream.read(buf)) != -1) {
                    baos.write(buf, 0, n);
                }
                rawData = baos.toByteArray();
            }
        }
        return rawData;
    }

    private IlbmImage loadDecoded() throws IOException {
        if (decoded == null) {
            decoded = IlbmCodec.read(loadRaw());
        }
        return decoded;
    }

    private BufferedImage loadBuffered() throws IOException {
        if (buffered == null) {
            buffered = IlbmUtility.toBufferedImage(loadDecoded());
        }
        return buffered;
    }

    // -------------------------------------------------------------------------
    // ImageReader implementation
    // -------------------------------------------------------------------------

    @Override
    public int getNumImages(boolean allowSearch) {
        return 1;
    }

    @Override
    public int getWidth(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return loadDecoded().bmhd().width();
    }

    @Override
    public int getHeight(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return loadDecoded().bmhd().height();
    }

    @Override
    public Iterator<ImageTypeSpecifier> getImageTypes(int imageIndex) throws IOException {
        checkIndex(imageIndex);
        return Collections.singletonList(
                ImageTypeSpecifier.createFromRenderedImage(loadBuffered())
        ).iterator();
    }

    @Override
    public IIOMetadata getStreamMetadata() {
        return null;
    }

    @Override
    public IIOMetadata getImageMetadata(int imageIndex) {
        return null;
    }

    @Override
    public BufferedImage read(int imageIndex, ImageReadParam param) throws IOException {
        checkIndex(imageIndex);
        return loadBuffered();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static void checkIndex(int imageIndex) {
        if (imageIndex != 0) {
            throw new IndexOutOfBoundsException("imageIndex must be 0, got " + imageIndex);
        }
    }
}
