/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.PacPicBankDto;
import dev.rambris.iff.codec.AmigaScreenMode;
import dev.rambris.iff.codec.IlbmCodec;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link PacPicBank} from sidecar JSON metadata written by {@link PacPicBankExporter}.
 *
 * <p>File references inside the JSON are resolved relative to {@code jsonPath}.
 *
 * <p>When {@link #withOptimize(boolean) optimize} is enabled, a greedy pairwise-swap palette
 * search runs before compression; see {@link PacPicPaletteOptimizer} for details.
 */
public class PacPicBankImporter {

    private boolean optimize = false;

    /** Enable or disable palette optimisation before compression. */
    public PacPicBankImporter withOptimize(boolean value) {
        this.optimize = value;
        return this;
    }

    public PacPicBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), PacPicBankDto.class);

        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 15);
        var chipRam = dto.chipRam() != null && dto.chipRam();
        var srcX = dto.srcX();
        var srcY = dto.srcY();
        var planes = dto.planes();
        var imageFile = dto.imageFile() != null ? dto.imageFile() : defaultImageFilename(jsonPath);
        var imagePath = jsonPath.resolveSibling(imageFile);

        var lowerImageFile = imageFile.toLowerCase();
        if (lowerImageFile.endsWith(".iff") || lowerImageFile.endsWith(".ilbm")) {
            var camgMode = IlbmCodec.read(imagePath).camgMode();
            if (AmigaScreenMode.isHam(camgMode)) {
                throw new NotSupportedException("Pac.Pic import of HAM images is not supported: " + imagePath);
            }
            if (AmigaScreenMode.isEhb(camgMode)) {
                throw new NotSupportedException(
                        "Pac.Pic import of Extra-HalfBrite images is not supported: " + imagePath);
            }
        }

        var image = IndexedPngWriter.readPixels(imagePath);

        if (planes <= 0) {
            planes = colorModelToPlanes(image.numColors());
        }

        var pixels = image.pixels();
        int[] amosPalette = null;
        if (dto.screen() != null) {
            amosPalette = parsePalette(dto.screen().palette());
            // If the JSON palette is all zeros (placeholder), use the image's own CMAP instead.
            if (AmigaPalette.isBlank(amosPalette) && image.palette24() != null) {
                amosPalette = new int[32];
                for (int i = 0; i < Math.min(32, image.palette24().length); i++) {
                    amosPalette[i] = AmigaPalette.from24Bit(image.palette24()[i]);
                }
            }
        }

        if (optimize) {
            int numColors = Math.max(1, 1 << planes);
            var result = PacPicPaletteOptimizer.optimize(pixels, numColors, srcX, srcY, planes);
            pixels = result.pixels();
            if (amosPalette != null) {
                amosPalette = PacPicPaletteOptimizer.applyPermutation(amosPalette, result.permutation());
            }
        }

        var picData = PacPicEncoder.compress(pixels, srcX, srcY, planes);

        PacPicBank.ScreenHeader screenHeader = null;
        if (dto.screen() != null) {
            var s = dto.screen();
            var palette = amosPalette != null ? amosPalette : parsePalette(s.palette());
            // Standard AMOS lores screen hardware start positions
            int hardX = s.hardX() != 0 ? s.hardX() : 0x81;
            int hardY = s.hardY() != 0 ? s.hardY() : 0x32;
            // bplCon0: (numPlanes << 12) | 0x0200 (COLOR bit) for standard lores screen
            int effectivePlanes = s.numPlanes() != 0 ? s.numPlanes() : planes;
            int bplCon0 = s.bplCon0() != 0 ? s.bplCon0() : (effectivePlanes << 12) | 0x0200;
            screenHeader = new PacPicBank.ScreenHeader(
                    s.width() != 0 ? s.width() : image.width(),
                    s.height() != 0 ? s.height() : image.height(),
                    hardX,
                    hardY,
                    s.displayWidth() != 0 ? s.displayWidth() : image.width(),
                    s.displayHeight() != 0 ? s.displayHeight() : image.height(),
                    s.offsetX(),
                    s.offsetY(),
                    bplCon0,
                    s.numColors() != 0 ? s.numColors() : (1 << planes),
                    effectivePlanes,
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

    private static int[] parsePalette(List<String> paletteList) {
        var palette = new int[32];
        if (paletteList == null) return palette;
        for (int i = 0; i < Math.min(32, paletteList.size()); i++) {
            palette[i] = AmigaPalette.parseHexRgb(paletteList.get(i));
        }
        return palette;
    }

    private static int colorModelToPlanes(int nColors) {
        var planes = 0;
        while ((1 << planes) < nColors) planes++;
        return Math.max(planes, 1);
    }
}
