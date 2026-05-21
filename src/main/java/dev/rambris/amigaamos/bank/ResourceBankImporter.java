/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.ResourceBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link ResourceBank} from a JSON metadata file previously produced by
 * {@link ResourceBankExporter}.
 *
 * <p>Usage: call {@link #importFrom(Path)} with the path to the {@code bank.json} file.
 * All other files (spritesheet PNG, {@code program_NNN.amui}) are resolved as siblings
 * of the JSON file using the filenames stored within it.
 *
 * <p>Example — given {@code jsonPath = "/path/to/exported/bank.json"} containing
 * {@code "spritesheet": "dark-boxes.png"}:
 * <pre>
 *   spritesheet resolved to: /path/to/exported/dark-boxes.png
 * </pre>
 *
 * <p>The number of bitplanes is derived from the PNG's {IndexColorModel} map size,
 * matching what {@link ResourceBankExporter} wrote.  The palette is read from {@code bank.json};
 * values are stored as {@code "#RGB"} (3 hex nibbles, Amiga 12-bit encoding).
 */
public class ResourceBankImporter {


    /**
     * Imports a {@link ResourceBank} from the given JSON metadata file.
     *
     * @param jsonPath path to the {@code bank.json} metadata file
     * @return the reconstructed in-memory bank
     * @throws IOException              if any file cannot be read
     * @throws IllegalStateException    if the spritesheet is not an indexed-colour PNG
     */
    public ResourceBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), ResourceBankDto.class);

        short bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 1);
        boolean chipRam = dto.chipRam() == null || dto.chipRam();
        var screenMode = parseHex(dto.screenMode() != null ? dto.screenMode() : "0x0000");
        var imagePath = dto.imagePath() != null ? dto.imagePath() : "";

        var palette = parsePalette(dto.palette());

        var spritesheetFile = dto.spritesheet() != null ? dto.spritesheet() : "spritesheet.png";
        var sheet = IndexedPngWriter.readPixels(jsonPath.resolveSibling(spritesheetFile));

        var numColours = dto.numColours();
        var planes = numColours > 0
                ? colorModelToPlanes(numColours)
                : colorModelToPlanes(sheet.numColors());

        var elements = parseElements(dto.elements(), sheet.pixels(), planes);
        var texts = parseTexts(dto.texts());
        var programs = parsePrograms(dto.programs(), jsonPath.getParent());

        return new ResourceBank(bankNumber, chipRam, screenMode, palette, imagePath,
                List.copyOf(elements), List.copyOf(texts), List.copyOf(programs));
    }

    // -------------------------------------------------------------------------
    // Elements
    // -------------------------------------------------------------------------

    private List<ResourceBank.Element> parseElements(
            List<ResourceBankDto.ElementDto> elementDtos, int[][] sheetPixels, int planes) {
        var elements = new ArrayList<ResourceBank.Element>();
        if (elementDtos == null) return elements;
        for (var el : elementDtos) {
            var images = parseImages(el.images(), sheetPixels, planes);
            elements.add(new ResourceBank.Element(el.name(), el.type(), images));
        }
        return elements;
    }

    private List<ResourceBank.Image> parseImages(
            List<ResourceBankDto.ImageDto> imageDtos, int[][] sheetPixels, int planes) {
        var images = new ArrayList<ResourceBank.Image>();
        if (imageDtos == null) return images;
        for (var img : imageDtos) {
            var pixels = extractRegion(sheetPixels, img.x(), img.y(), img.width(), img.height());
            var data = PacPicEncoder.compress(pixels, img.x(), img.y(), planes);
            images.add(new ResourceBank.Image(img.x(), img.y(), img.width(), img.height(), planes, data));
        }
        return images;
    }

    // -------------------------------------------------------------------------
    // Texts
    // -------------------------------------------------------------------------

    private List<String> parseTexts(List<ResourceBankDto.TextDto> textDtos) {
        var texts = new ArrayList<String>();
        if (textDtos == null) return texts;
        for (var t : textDtos) {
            texts.add(t.text() != null ? t.text() : "");
        }
        return texts;
    }

    // -------------------------------------------------------------------------
    // Programs
    // -------------------------------------------------------------------------

    private List<String> parsePrograms(List<ResourceBankDto.ProgramDto> programDtos, Path dir)
            throws IOException {
        var programs = new ArrayList<String>();
        if (programDtos == null) return programs;
        for (var p : programDtos) {
            programs.add(Files.readString(dir.resolve(p.file()), java.nio.charset.StandardCharsets.UTF_8));
        }
        return programs;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int[] parsePalette(List<String> paletteList) {
        var palette = new int[32];
        if (paletteList == null) return palette;
        for (int i = 0; i < Math.min(paletteList.size(), 32); i++) {
            palette[i] = AmigaPalette.parseHexRgb(paletteList.get(i));
        }
        return palette;
    }

    /** Parses a hex string like {@code "0x0000"} or {@code "0"} to an int. */
    private static int parseHex(String s) {
        if (s.startsWith("0x") || s.startsWith("0X")) return Integer.parseInt(s.substring(2), 16);
        return Integer.parseInt(s);
    }

    /** Computes the number of bitplanes needed to represent {@code nColors} colours. */
    private static int colorModelToPlanes(int nColors) {
        int planes = 0;
        while ((1 << planes) < nColors) planes++;
        return Math.max(planes, 1);
    }

    /** Extracts a rectangular region of palette-index pixels from a sheet. */
    private static int[][] extractRegion(int[][] sheet, int x, int y, int w, int h) {
        var pixels = new int[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                pixels[row][col] = sheet[y + row][x + col];
            }
        }
        return pixels;
    }
}
