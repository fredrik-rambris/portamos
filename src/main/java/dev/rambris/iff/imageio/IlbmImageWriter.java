/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.iff.imageio;

import dev.rambris.iff.IlbmUtility;
import dev.rambris.iff.codec.*;

import javax.imageio.IIOImage;
import javax.imageio.ImageTypeSpecifier;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.IOException;

/**
 * ImageIO writer for IFF ILBM files.
 *
 * <p>Only indexed-colour ({@link java.awt.image.IndexColorModel}) images are supported.
 * The number of bitplanes is derived from the palette size ({@code ceil(log2(paletteSize))}).
 * The pixel aspect ratio is derived from the image dimensions via
 * {@link AmigaScreenMode#aspectFromDimensions(int, int)}.
 *
 * <p>Compression is controlled via {@link IlbmWriteParam}:
 * <pre>{@code
 * var param = (IlbmWriteParam) writer.getDefaultWriteParam();
 * param.setCompressionType(IlbmWriteParam.COMPRESSION_BYTERUN1);
 * writer.write(null, new IIOImage(image, null, null), param);
 * }</pre>
 */
public final class IlbmImageWriter extends ImageWriter {

    IlbmImageWriter(IlbmImageWriterSpi originatingProvider) {
        super(originatingProvider);
    }

    // -------------------------------------------------------------------------
    // Parameters
    // -------------------------------------------------------------------------

    @Override
    public ImageWriteParam getDefaultWriteParam() {
        return new IlbmWriteParam();
    }

    // -------------------------------------------------------------------------
    // Metadata (not supported)
    // -------------------------------------------------------------------------

    @Override
    public IIOMetadata getDefaultStreamMetadata(ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata getDefaultImageMetadata(ImageTypeSpecifier imageType, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertStreamMetadata(IIOMetadata inData, ImageWriteParam param) {
        return null;
    }

    @Override
    public IIOMetadata convertImageMetadata(IIOMetadata inData, ImageTypeSpecifier imageType,
                                            ImageWriteParam param) {
        return null;
    }

    // -------------------------------------------------------------------------
    // Write
    // -------------------------------------------------------------------------

    @Override
    public void write(IIOMetadata streamMetadata, IIOImage iioImage, ImageWriteParam param)
            throws IOException {

        var rendered = iioImage.getRenderedImage();
        if (!(rendered instanceof BufferedImage image)) {
            throw new IOException(
                    "IlbmImageWriter requires a BufferedImage; got "
                    + rendered.getClass().getSimpleName());
        }

        boolean useByteRun1 = param instanceof IlbmWriteParam
                && IlbmWriteParam.COMPRESSION_BYTERUN1.equals(param.getCompressionType());

        var palette = IlbmUtility.extractPalette(image);
        int planes  = Math.max(1, (int) Math.ceil(Math.log(palette.length) / Math.log(2)));
        var body   = IlbmUtility.toPlanar(image, planes);

        int width  = image.getWidth();
        int height = image.getHeight();
        var aspect = AmigaScreenMode.aspectFromDimensions(width, height);

        var bmhd = new BmhdChunk(width, height, 0, 0, planes, 0,
                BmhdChunk.COMPRESSION_NONE, 0, aspect[0], aspect[1], width, height);
        var ilbmImage = new IlbmImage(bmhd, palette, 0, body);

        var options = useByteRun1
                ? new IlbmOptions[]{IlbmOptions.COMPRESSION_BYTERUN1}
                : new IlbmOptions[0];

        var encoded = IlbmCodec.write(ilbmImage, options);

        var stream = (ImageOutputStream) output;
        stream.write(encoded);
        stream.flush();
    }
}
