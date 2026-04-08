/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Path;

/**
 * Exports a {@link PacPicBank} to a PNG and sidecar JSON metadata.
 *
 * <p>API contract:
 * <ul>
 *   <li>Writes image to {@code pngPath}</li>
 *   <li>Writes metadata to {@code pngPath + ".json"}</li>
 *   <li>Does not create directories</li>
 * </ul>
 */
public class PacPicBankExporter {

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void export(PacPicBank bank, Path pngPath) throws IOException {
        var pixels = PacPicDecoder.decompress(bank.picData());
        var height = pixels.length;
        var width = height > 0 ? pixels[0].length : 0;

        var planes = readPlanes(bank.picData());
        var maxColors = Math.max(1 << planes, maxIndex(pixels) + 1);
        var palette = bank.isSpack()
                ? bank.screenHeader().palette()
                : new int[32];

        var image = toIndexedImage(pixels, width, height, palette, maxColors);
        ImageIO.write(image, "PNG", pngPath.toFile());

        var jsonPath = Path.of(pngPath.toString() + ".json");
        var root = JSON.createObjectNode();
        root.put("type", "PacPic");
        root.put("bankNumber", bank.bankNumber());
        root.put("chipRam", bank.chipRam());
        root.put("pngFile", pngPath.getFileName().toString());
        root.put("srcX", readSrcX(bank.picData()));
        root.put("srcY", readSrcY(bank.picData()));
        root.put("planes", planes);
        root.put("spack", bank.isSpack());

        if (bank.isSpack()) {
            var sh = bank.screenHeader();
            var s = root.putObject("screen");
            s.put("width", sh.width());
            s.put("height", sh.height());
            s.put("hardX", sh.hardX());
            s.put("hardY", sh.hardY());
            s.put("displayWidth", sh.displayWidth());
            s.put("displayHeight", sh.displayHeight());
            s.put("offsetX", sh.offsetX());
            s.put("offsetY", sh.offsetY());
            s.put("bplCon0", sh.bplCon0());
            s.put("numColors", sh.numColors());
            s.put("numPlanes", sh.numPlanes());
            var p = s.putArray("palette");
            for (var c : sh.palette()) {
                p.add(AmigaPalette.toHexRgb(c));
            }
        }

        JSON.writeValue(jsonPath.toFile(), root);
    }

    private static BufferedImage toIndexedImage(
            int[][] pixels, int width, int height, int[] amigaPalette, int maxColors) {
        var cm = AmigaPalette.buildIndexColorModel(amigaPalette, maxColors);
        var image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, cm);
        var raster = image.getRaster();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                raster.setSample(x, y, 0, pixels[y][x]);
            }
        }
        return image;
    }

    private static int maxIndex(int[][] pixels) {
        var max = 0;
        for (var row : pixels) {
            for (var px : row) {
                if (px > max) max = px;
            }
        }
        return max;
    }

    private static int readSrcX(byte[] picData) {
        // pkdx stored in bytes at offset +4
        var pkdx = ((picData[PacPicFormat.OFF_PKDX] & 0xFF) << 8) | (picData[PacPicFormat.OFF_PKDX + 1] & 0xFF);
        return pkdx * 8;
    }

    private static int readSrcY(byte[] picData) {
        return ((picData[PacPicFormat.OFF_PKDY] & 0xFF) << 8) | (picData[PacPicFormat.OFF_PKDY + 1] & 0xFF);
    }

    private static int readPlanes(byte[] picData) {
        return ((picData[PacPicFormat.OFF_PKPLAN] & 0xFF) << 8) | (picData[PacPicFormat.OFF_PKPLAN + 1] & 0xFF);
    }
}

