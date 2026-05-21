/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.PacPicBankDto;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link PacPicBank} to a PNG and sidecar JSON metadata.
 *
 * <p>API contract:
 * <ul>
 *   <li>Writes image to {@code imagePath}</li>
 *   <li>Writes metadata to {@code imagePath + ".json"}</li>
 *   <li>Does not create directories</li>
 * </ul>
 */
public class PacPicBankExporter {


    public void export(PacPicBank bank, Path imagePath) throws IOException {
        export(bank, imagePath, false);
    }

    /**
     * Exports the PacPic bank to {@code imagePath}.
     *
     * @param bank      the PacPic bank to export
     * @param imagePath destination image file path (extension should match {@code ilbm})
     * @param ilbm      if {@code true}, write as IFF ILBM; otherwise PNG
     * @throws IOException if any file cannot be written
     */
    public void export(PacPicBank bank, Path imagePath, boolean ilbm) throws IOException {
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
                imagePath.getFileName().toString(),
                readSrcX(bank.picData()),
                readSrcY(bank.picData()),
                planes,
                bank.isSpack(),
                screenDto);

        JSON.writeValue(Path.of(imagePath + ".json").toFile(), dto);
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
