/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

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

        var image = IndexedPngWriter.readPixels(imagePath);

        if (planes <= 0) {
            planes = colorModelToPlanes(image.numColors());
        }

        var picData = PacPicEncoder.compress(image.pixels(), srcX, srcY, planes);

        PacPicBank.ScreenHeader screenHeader = null;
        if (spack) {
            var s = root.path("screen");
            var palette = parsePalette(s.path("palette"));
            screenHeader = new PacPicBank.ScreenHeader(
                    s.path("width").asInt(image.width()),
                    s.path("height").asInt(image.height()),
                    s.path("hardX").asInt(0),
                    s.path("hardY").asInt(0),
                    s.path("displayWidth").asInt(image.width()),
                    s.path("displayHeight").asInt(image.height()),
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

