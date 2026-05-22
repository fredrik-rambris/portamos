/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.PacPicBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link PacPicBank} to a JSON metadata file and a sibling image file.
 *
 * <p>API contract:
 * <ul>
 *   <li>Writes metadata to {@code jsonPath}</li>
 *   <li>Writes image to {@code stem(jsonPath).png} (or {@code .iff}) alongside the JSON</li>
 *   <li>Creates the containing directory if absent</li>
 * </ul>
 */
public class PacPicBankExporter {


    public void export(PacPicBank bank, Path jsonPath) throws IOException {
        export(bank, jsonPath, false);
    }

    /**
     * Exports the PacPic bank to {@code jsonPath}.
     *
     * @param bank     the PacPic bank to export
     * @param jsonPath destination JSON metadata file; image is written as a sibling
     * @param ilbm     if {@code true}, write image as IFF ILBM; otherwise PNG
     * @throws IOException if any file cannot be written
     */
    public void export(PacPicBank bank, Path jsonPath, boolean ilbm) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);

        var imageFilename = stem + (ilbm ? ".iff" : ".png");
        var imagePath = dir.resolve(imageFilename);

        var pixels = PacPicDecoder.decompress(bank.picData());
        var height = pixels.length;
        var width = height > 0 ? pixels[0].length : 0;

        var planes = readPlanes(bank.picData());
        var maxColors = Math.max(1 << planes, maxIndex(pixels) + 1);
        var palette = bank.isSpack()
                ? bank.screenHeader().palette()
                : new int[32];

        if (ilbm) {
            IndexedPngWriter.writeIlbm(palette, planes, pixels, width, height, imagePath);
        } else {
            IndexedPngWriter.writePng(palette, maxColors, pixels, width, height, imagePath);
        }

        PacPicBankDto.ScreenHeaderDto screenDto = null;
        if (bank.isSpack()) {
            var sh = bank.screenHeader();
            List<String> paletteList = Arrays.stream(sh.palette())
                    .mapToObj(AmigaPalette::toHexRgb)
                    .toList();
            screenDto = new PacPicBankDto.ScreenHeaderDto(
                    sh.width(), sh.height(), sh.hardX(), sh.hardY(),
                    sh.displayWidth(), sh.displayHeight(),
                    sh.offsetX(), sh.offsetY(),
                    sh.bplCon0(), sh.numColors(), sh.numPlanes(),
                    paletteList);
        }

        var dto = new PacPicBankDto(
                PacPicBankDto.TYPE,
                bank.bankNumber() & 0xFFFF,
                bank.chipRam(),
                imageFilename,
                readSrcX(bank.picData()),
                readSrcY(bank.picData()),
                planes,
                bank.isSpack(),
                screenDto);

        JSON.writeValue(jsonPath.toFile(), dto);
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
