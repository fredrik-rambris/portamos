/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.imageio;

import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriter;
import javax.imageio.spi.ImageWriterSpi;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.IndexColorModel;
import java.util.Locale;

/**
 * ImageIO service provider for writing IFF ILBM files.
 *
 * <p>Registered via {@code META-INF/services/javax.imageio.spi.ImageWriterSpi}.
 * Only indexed-colour images ({@link IndexColorModel}) are supported.
 */
public final class IlbmImageWriterSpi extends ImageWriterSpi {

    private static final String   VENDOR       = "dev.rambris";
    private static final String   VERSION      = "1.0";
    private static final String[] FORMAT_NAMES = {"ILBM", "IFF"};
    private static final String[] SUFFIXES     = {"iff", "lbm", "ilbm"};
    private static final String[] MIME_TYPES   = {"image/x-ilbm", "image/x-iff"};
    private static final String   WRITER_CLASS = "dev.rambris.iff.imageio.IlbmImageWriter";

    public IlbmImageWriterSpi() {
        super(VENDOR, VERSION, FORMAT_NAMES, SUFFIXES, MIME_TYPES, WRITER_CLASS,
                new Class<?>[]{ImageOutputStream.class},
                new String[]{"dev.rambris.iff.imageio.IlbmImageReaderSpi"},
                false, null, null, null, null,
                false, null, null, null, null);
    }

    @Override
    public boolean canEncodeImage(ImageTypeSpecifier type) {
        return type.getColorModel() instanceof IndexColorModel;
    }

    @Override
    public ImageWriter createWriterInstance(Object extension) {
        return new IlbmImageWriter(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "IFF ILBM (Amiga Interleaved Bitmap) writer";
    }
}
