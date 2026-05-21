/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.SpriteBankDto;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link SpriteBank} from a JSON metadata file previously produced by
 * {@link SpriteBankExporter}.
 *
 * <p>Usage: call {@link #importFrom(Path)} with the path to the {@code sprites.json} file.
 * Any file references in JSON (like {@code spritesheet}) are resolved relative to
 * {@code jsonPath} using {@link Path#resolveSibling(String)}.
 */
public class SpriteBankImporter {


    /**
     * Imports a sprite/icon bank from the given JSON metadata file.
     *
     * @param jsonPath path to {@code sprites.json}
     * @return reconstructed {@link SpriteBank}
     * @throws IOException if metadata or spritesheet cannot be read
     */
    public SpriteBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), SpriteBankDto.class);

        var bankType = parseBankType(dto.type());

        var spritesheetFile = dto.spritesheet() != null ? dto.spritesheet() : "spritesheet.png";
        var sheet = IndexedPngWriter.readPixels(jsonPath.resolveSibling(spritesheetFile));

        var numColours = dto.numColours();
        var defaultPlanes = numColours > 0
                ? colorModelToPlanes(numColours)
                : colorModelToPlanes(sheet.numColors());

        var palette = parsePalette(dto.palette());
        var sprites = parseSprites(dto.sprites(), sheet.pixels(), defaultPlanes);

        return new SpriteBank(bankType, List.copyOf(sprites), palette);
    }

    // -------------------------------------------------------------------------
    // Sprites
    // -------------------------------------------------------------------------

    private List<SpriteBank.Sprite> parseSprites(
            List<SpriteBankDto.SpriteDto> spriteDtos, int[][] sheetPixels, int defaultPlanes) {
        var sprites = new ArrayList<SpriteBank.Sprite>();
        if (spriteDtos == null) return sprites;

        for (var s : spriteDtos) {
            if (Boolean.TRUE.equals(s.empty())) {
                sprites.add(new SpriteBank.Sprite(0, 0, 0, 0, 0, new byte[0]));
                continue;
            }

            var width = s.width() != null ? s.width() : 0;
            if (width % 16 != 0) {
                System.err.println("Warning: sprite width " + width + " is not divisible by 16 — truncating to nearest word");
            }
            var widthWords = width / 16;
            if (widthWords < 1) {
                System.err.println("Invalid width on sprite: " + s);
            }

            var height = s.height() != null ? s.height() : 0;
            var planes = s.planes() != null ? s.planes() : defaultPlanes;
            var hotspotX = s.hotspotX() != null ? s.hotspotX() : 0;
            var hotspotY = s.hotspotY() != null ? s.hotspotY() : 0;
            var x = s.x() != null ? s.x() : 0;

            var data = extractPlanar(sheetPixels, x, 0, widthWords, height, planes);
            sprites.add(new SpriteBank.Sprite(widthWords, height, planes, hotspotX, hotspotY, data));
        }

        return sprites;
    }

    /**
     * Extracts planar bitmap bytes from an indexed raster region.
     *
     * <p>Output layout matches AMOS sprite/icon banks:
     * all rows for plane 0, then plane 1, ...; each row is widthWords big-endian words.
     */
    private static byte[] extractPlanar(
            int[][] sheetPixels, int x0, int y0, int widthWords, int height, int planes) {

        if (widthWords == 0 || height == 0 || planes == 0) {
            return new byte[0];
        }

        var planeStride = widthWords * 2 * height;
        var out = new byte[planeStride * planes];

        for (int p = 0; p < planes; p++) {
            for (int row = 0; row < height; row++) {
                for (int w = 0; w < widthWords; w++) {
                    var word = 0;
                    for (int bit = 0; bit < 16; bit++) {
                        var px = x0 + w * 16 + bit;
                        var py = y0 + row;
                        var idx = sheetPixels[py][px];
                        var b = (idx >> p) & 1;
                        word |= b << (15 - bit);
                    }
                    var off = p * planeStride + row * widthWords * 2 + w * 2;
                    out[off] = (byte) ((word >> 8) & 0xFF);
                    out[off + 1] = (byte) (word & 0xFF);
                }
            }
        }

        return out;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static AmosBank.Type parseBankType(String rawType) {
        var t = rawType == null ? "" : rawType.trim().toUpperCase();
        return switch (t) {
            case "ICON", "ICONS" -> AmosBank.Type.ICONS;
            default -> AmosBank.Type.SPRITES;
        };
    }

    private static int[] parsePalette(List<String> paletteList) {
        var palette = new int[32];
        if (paletteList == null) return palette;
        for (int i = 0; i < Math.min(paletteList.size(), 32); i++) {
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
