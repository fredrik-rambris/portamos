package dev.rambris.iff.imageio;

import javax.imageio.ImageWriteParam;
import java.util.Locale;

/**
 * Write parameters for {@link IlbmImageWriter}.
 *
 * <p>Supports two compression types:
 * <ul>
 *   <li>{@link #COMPRESSION_NONE} — raw uncompressed BODY (default)</li>
 *   <li>{@link #COMPRESSION_BYTERUN1} — PackBits (ByteRun1) per-strip compression</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>{@code
 * var param = new IlbmWriteParam();
 * param.setCompressionType(IlbmWriteParam.COMPRESSION_BYTERUN1);
 * writer.write(null, new IIOImage(image, null, null), param);
 * }</pre>
 *
 * <p>The compression mode is always {@link #MODE_EXPLICIT}.
 */
public final class IlbmWriteParam extends ImageWriteParam {

    /** Write uncompressed BODY data. */
    public static final String COMPRESSION_NONE      = "None";
    /** Compress BODY with ByteRun1 (PackBits). */
    public static final String COMPRESSION_BYTERUN1  = "ByteRun1";

    public IlbmWriteParam() {
        this(Locale.getDefault());
    }

    public IlbmWriteParam(Locale locale) {
        super(locale);
        canWriteCompressed = true;
        compressionMode    = MODE_EXPLICIT;
        compressionType    = COMPRESSION_NONE;
        compressionTypes   = new String[]{COMPRESSION_NONE, COMPRESSION_BYTERUN1};
    }
}
