/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.SpriteBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a parsed {@link SpriteBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code spritesheet.png} — 8-bit indexed PNG; all non-empty sprites composited
 *       side-by-side in bank order, starting at x=0, y=0.</li>
 *   <li>{@code sprites.json} — metadata: palette, per-sprite dimensions, hot-spots, and
 *       the x-offset of each sprite within the spritesheet.</li>
 * </ul>
 *
 * <p>Palette and colour depth are taken from the bank's 32-entry palette.  The number of
 * colours used by the spritesheet is {@code 1 << maxPlanes} where {@code maxPlanes} is the
 * deepest sprite in the bank.
 */
public class SpriteBankExporter {

    /**
     * Exports the sprite bank to {@code jsonPath} (PNG spritesheet alongside, default).
     *
     * @param bank     the sprite bank to export
     * @param jsonPath destination JSON metadata file; data files are written as siblings
     * @throws IOException if any file cannot be written
     */
    public void export(SpriteBank bank, Path jsonPath) throws IOException {
        export(bank, jsonPath, false);
    }

    /**
     * Exports the sprite bank to {@code jsonPath}.
     *
     * @param bank     the sprite bank to export
     * @param jsonPath destination JSON metadata file; data files are written as siblings
     * @param ilbm     if {@code true}, write the spritesheet as an IFF ILBM; otherwise PNG
     * @throws IOException if any file cannot be written
     */
    public void export(SpriteBank bank, Path jsonPath, boolean ilbm) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        Files.createDirectories(dir);
        var stem = AmosBankService.stem(jsonPath);
        var spritesheetName = stem + (ilbm ? ".iff" : ".png");
        exportSpritesheet(bank, dir.resolve(spritesheetName), ilbm);
        exportMetadata(bank, jsonPath, spritesheetName);
    }

    // -------------------------------------------------------------------------
    // Spritesheet
    // -------------------------------------------------------------------------

    private void exportSpritesheet(SpriteBank bank, Path dest, boolean ilbm) throws IOException {
        // Determine sheet dimensions and max colour depth
        int sheetW = 0, sheetH = 0, maxPlanes = 0;
        for (var s : bank.sprites()) {
            if (s.isEmpty()) continue;
            sheetW += s.widthPixels();
            sheetH = Math.max(sheetH, s.height());
            maxPlanes = Math.max(maxPlanes, s.planes());
        }

        if (sheetW == 0 || sheetH == 0) {
            System.out.println("No sprites to export.");
            return;
        }

        int maxColors = 1 << maxPlanes;
        var allPixels = new int[sheetH][sheetW];
        int x = 0;
        for (var sprite : bank.sprites()) {
            if (sprite.isEmpty()) continue;
            var pixels = toIndexedPixels(sprite);
            for (int py = 0; py < sprite.height(); py++) {
                for (int px = 0; px < sprite.widthPixels(); px++) {
                    allPixels[py][x + px] = pixels[py][px];
                }
            }
            x += sprite.widthPixels();
        }

        if (ilbm) {
            IndexedPngWriter.writeIlbm(bank.palette(), maxPlanes, allPixels, sheetW, sheetH, dest);
        } else {
            IndexedPngWriter.writePng(bank.palette(), maxColors, allPixels, sheetW, sheetH, dest);
        }
        System.out.printf("Sprite sheet: %dx%d px, %d sprites → %s%n",
                sheetW, sheetH, bank.sprites().size(), dest);
    }

    /**
     * Converts Amiga planar bitmap data to a 2-D array of colour indices.
     *
     * <p>Layout in {@code sprite.data()}: all rows of plane 0, then plane 1, … Each row is
     * {@code widthWords} big-endian 16-bit words; the MSB of the first word is the leftmost pixel.
     */
    private static int[][] toIndexedPixels(SpriteBank.Sprite sprite) {
        var w = sprite.widthWords();
        var h = sprite.height();
        var d = sprite.planes();
        var raw = sprite.data();
        var planeStride = w * 2 * h; // bytes per plane

        var pixels = new int[h][w * 16];
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w * 16; px++) {
                var colorIndex = 0;
                for (int p = 0; p < d; p++) {
                    // word address within this plane's data
                    var wordOff = p * planeStride + py * w * 2 + (px / 16) * 2;
                    var wordVal = ((raw[wordOff] & 0xFF) << 8) | (raw[wordOff + 1] & 0xFF);
                    var bit = (wordVal >> (15 - (px % 16))) & 1;
                    colorIndex |= (bit << p);
                }
                pixels[py][px] = colorIndex;
            }
        }
        return pixels;
    }

    // -------------------------------------------------------------------------
    // Metadata JSON
    // -------------------------------------------------------------------------

    private void exportMetadata(SpriteBank bank, Path jsonPath, String spritesheetName)
            throws IOException {
        var maxPlanes = bank.sprites().stream()
                .filter(s -> !s.isEmpty())
                .mapToInt(SpriteBank.Sprite::planes)
                .max().orElse(0);

        var paletteList = Arrays.stream(bank.palette())
                .mapToObj(AmigaPalette::toHexRgb)
                .toList();

        var spriteDtos = new ArrayList<SpriteBankDto.SpriteDto>();
        int sheetX = 0;
        for (int i = 0; i < bank.sprites().size(); i++) {
            var sprite = bank.sprites().get(i);
            if (sprite.isEmpty()) {
                spriteDtos.add(new SpriteBankDto.SpriteDto(i, true, null, null, null, null, null, null));
            } else {
                spriteDtos.add(new SpriteBankDto.SpriteDto(
                        i, null,
                        sheetX,
                        sprite.widthPixels(),
                        sprite.height(),
                        sprite.planes(),
                        sprite.hotspotX() != 0 ? sprite.hotspotX() : null,
                        sprite.hotspotY() != 0 ? sprite.hotspotY() : null));
                sheetX += sprite.widthPixels();
            }
        }

        var dto = new SpriteBankDto(
                bank.type() == AmosBank.Type.ICONS ? SpriteBankDto.TYPE_ICON : SpriteBankDto.TYPE_SPRITE,
                null, null,   // bankNumber / chipRam: not stored in AmSp/AmIc format
                spritesheetName,
                maxPlanes > 0 ? 1 << maxPlanes : 0,
                paletteList,
                List.copyOf(spriteDtos));

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s%n", jsonPath);
    }
}
