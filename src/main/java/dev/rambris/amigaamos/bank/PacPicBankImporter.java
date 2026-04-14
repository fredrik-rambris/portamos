/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Imports a {@link PacPicBank} from sidecar JSON metadata written by {@link PacPicBankExporter}.
 *
 * <p>File references inside the JSON are resolved relative to {@code jsonPath}.
 */
public class PacPicBankImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    public PacPicBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var bankNumber = (short) root.path("bankNumber").asInt(15);
        var chipRam = root.path("chipRam").asBoolean(true);
        var srcX = root.path("srcX").asInt(0);
        var srcY = root.path("srcY").asInt(0);
        var planes = root.path("planes").asInt(1);
        var spack = root.path("spack").asBoolean(false);

        var imageFile = root.has("imageFile")
                ? root.path("imageFile").asText()
                : root.path("pngFile").asText(defaultImageFilename(jsonPath));
        var imagePath = jsonPath.resolveSibling(imageFile);

        var image = ImageIO.read(imagePath.toFile());
        if (image == null) throw new IOException("Cannot read image: " + imagePath);
        if (!(image.getColorModel() instanceof IndexColorModel cm)) {
            throw new IllegalStateException("Image must be indexed-colour: " + imagePath);
        }

        if (planes <= 0) {
            planes = colorModelToPlanes(cm.getMapSize());
        }

        var raster = image.getRaster();
        var pixels = extractPixels(raster, image.getWidth(), image.getHeight());
        var picData = PacPicEncoder.compress(pixels, srcX, srcY, planes);

        PacPicBank.ScreenHeader screenHeader = null;
        if (spack) {
            var s = root.path("screen");
            var palette = parsePalette(s.path("palette"));
            screenHeader = new PacPicBank.ScreenHeader(
                    s.path("width").asInt(image.getWidth()),
                    s.path("height").asInt(image.getHeight()),
                    s.path("hardX").asInt(0),
                    s.path("hardY").asInt(0),
                    s.path("displayWidth").asInt(image.getWidth()),
                    s.path("displayHeight").asInt(image.getHeight()),
                    s.path("offsetX").asInt(0),
                    s.path("offsetY").asInt(0),
                    s.path("bplCon0").asInt(0),
                    s.path("numColors").asInt(1 << planes),
                    s.path("numPlanes").asInt(planes),
                    palette
            );
        }

        return new PacPicBank(bankNumber, chipRam, screenHeader, picData);
    }

    private static String defaultImageFilename(Path jsonPath) {
        var s = jsonPath.getFileName().toString();
        if (s.endsWith(".json")) {
            return s.substring(0, s.length() - 5);
        }
        return "image.png";
    }

    private static int[][] extractPixels(WritableRaster raster, int w, int h) {
        var pixels = new int[h][w];
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                pixels[y][x] = raster.getSample(x, y, 0);
            }
        }
        return pixels;
    }

    private static int[] parsePalette(JsonNode paletteNode) {
        var palette = new int[32];
        if (paletteNode.isMissingNode()) return palette;
        for (int i = 0; i < Math.min(32, paletteNode.size()); i++) {
            palette[i] = AmigaPalette.parseHexRgb(paletteNode.get(i).asText("#000"));
        }
        return palette;
    }

    private static int colorModelToPlanes(int nColors) {
        var planes = 0;
        while ((1 << planes) < nColors) planes++;
        return Math.max(planes, 1);
    }
}

