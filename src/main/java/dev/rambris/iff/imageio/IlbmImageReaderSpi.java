/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.imageio;

import javax.imageio.ImageReader;
import javax.imageio.spi.ImageReaderSpi;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.util.Locale;

/**
 * ImageIO service provider for reading IFF ILBM files.
 *
 * <p>Registered via {@code META-INF/services/javax.imageio.spi.ImageReaderSpi}.
 * Detection checks for the {@code FORM} + {@code ILBM} magic at the start of the stream.
 */
public final class IlbmImageReaderSpi extends ImageReaderSpi {

    private static final String   VENDOR       = "dev.rambris";
    private static final String   VERSION      = "1.0";
    private static final String[] FORMAT_NAMES = {"ILBM", "IFF"};
    private static final String[] SUFFIXES     = {"iff", "lbm", "ilbm"};
    private static final String[] MIME_TYPES   = {"image/x-ilbm", "image/x-iff"};
    private static final String   READER_CLASS = "dev.rambris.iff.imageio.IlbmImageReader";

    public IlbmImageReaderSpi() {
        super(VENDOR, VERSION, FORMAT_NAMES, SUFFIXES, MIME_TYPES, READER_CLASS,
                new Class<?>[]{ImageInputStream.class},
                new String[]{"dev.rambris.iff.imageio.IlbmImageWriterSpi"},
                false, null, null, null, null,
                false, null, null, null, null);
    }

    @Override
    public boolean canDecodeInput(Object source) throws IOException {
        if (!(source instanceof ImageInputStream stream)) return false;
        stream.mark();
        try {
            var header = new byte[12];
            if (stream.read(header) < 12) return false;
            // "FORM" at bytes 0-3, "ILBM" at bytes 8-11
            return header[0] == 'F' && header[1] == 'O' && header[2] == 'R' && header[3] == 'M'
                && header[8] == 'I' && header[9] == 'L' && header[10] == 'B' && header[11] == 'M';
        } finally {
            stream.reset();
        }
    }

    @Override
    public ImageReader createReaderInstance(Object extension) {
        return new IlbmImageReader(this);
    }

    @Override
    public String getDescription(Locale locale) {
        return "IFF ILBM (Amiga Interleaved Bitmap) reader";
    }
}
