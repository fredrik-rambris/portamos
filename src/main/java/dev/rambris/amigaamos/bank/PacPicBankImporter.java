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
 * search runs before compression; see {@link PaletteOptimizer} for details.
 */
public class PacPicBankImporter {

    private boolean optimize = false;
    private boolean pad = false;

    /** Largest padding (pixels) tried in each direction by {@link #withPad(boolean)}. */
    public static final int MAX_PAD = 16;

    /** Enable or disable palette optimisation before compression. */
    public PacPicBankImporter withOptimize(boolean value) {
        this.optimize = value;
        return this;
    }

    /**
     * Enable or disable the padding pass: after any palette optimisation, try growing the
     * picture by 1..{@value #MAX_PAD} pixels in width and height (see {@link PaddingOptimizer}).
     * If padding is chosen, the screen header is enlarged so the picture still fits; the
     * display size is left unchanged.
     */
    public PacPicBankImporter withPad(boolean value) {
        this.pad = value;
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
            var result = PaletteOptimizer.optimize(pixels, numColors,
                    PaletteOptimizer.pacPicSize(srcX, srcY, planes));
            pixels = result.pixels();
            if (amosPalette != null) {
                amosPalette = PaletteOptimizer.applyPermutation(amosPalette, result.permutation());
            }
        }

        int padX = 0, padY = 0;
        if (pad) {
            var result = PaddingOptimizer.optimize(pixels, MAX_PAD, PaletteOptimizer.pacPicSize(srcX, srcY, planes));
            pixels = result.pixels();
            padX = result.padX();
            padY = result.padY();
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
            int screenWidth = s.width() != 0 ? s.width() : image.width();
            int screenHeight = s.height() != 0 ? s.height() : image.height();
            if (padX > 0) {
                // Picture grew: make sure the screen bitmap still holds it (AMOS screens are 16-px aligned)
                screenWidth = Math.max(screenWidth, (srcX + image.width() + padX + 15) & ~15);
            }
            if (padY > 0) {
                screenHeight = Math.max(screenHeight, srcY + image.height() + padY);
            }
            screenHeader = new PacPicBank.ScreenHeader(
                    screenWidth,
                    screenHeight,
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
