/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 * <p>The number of bitplanes is derived from the PNG's {@link IndexColorModel} map size,
 * matching what {@link ResourceBankExporter} wrote.  The palette is read from {@code bank.json};
 * values are stored as {@code "#RGB"} (3 hex nibbles, Amiga 12-bit encoding).
 */
public class ResourceBankImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Imports a {@link ResourceBank} from the given JSON metadata file.
     *
     * @param jsonPath path to the {@code bank.json} metadata file
     * @return the reconstructed in-memory bank
     * @throws IOException              if any file cannot be read
     * @throws IllegalStateException    if the spritesheet is not an indexed-colour PNG
     */
    public ResourceBank importFrom(Path jsonPath) throws IOException {
        JsonNode root = JSON.readTree(jsonPath.toFile());

        short bankNumber = (short) root.path("bankNumber").asInt(1);
        boolean chipRam  = root.path("chipRam").asBoolean(true);
        int screenMode   = parseHex(root.path("screenMode").asText("0x0000"));
        String imagePath = root.path("imagePath").asText("");

        int[] palette = parsePalette(root.path("palette"));

        String spritesheetFile = root.path("spritesheet").asText("spritesheet.png");
        Path spritesheetPath = jsonPath.resolveSibling(spritesheetFile);

        // Load spritesheet; derive bitplane count from numColours in JSON (preferred)
        // because Java always writes 8-bit indexed PNGs regardless of the original depth.
        BufferedImage sheet = ImageIO.read(spritesheetPath.toFile());
        if (sheet == null) throw new IOException("Cannot read spritesheet: " + spritesheetPath);
        if (!(sheet.getColorModel() instanceof IndexColorModel cm)) {
            throw new IllegalStateException("Spritesheet must be an indexed-colour image: " + spritesheetPath);
        }
        int numColours = root.path("numColours").asInt(0);
        int planes = numColours > 0 ? colorModelToPlanes(numColours) : colorModelToPlanes(cm.getMapSize());
        WritableRaster raster = sheet.getRaster();

        List<ResourceBank.Element> elements = parseElements(root.path("elements"), raster, planes);
        List<String> texts    = parseTexts(root.path("texts"));
        List<String> programs = parsePrograms(root.path("programs"), jsonPath.getParent());

        return new ResourceBank(bankNumber, chipRam, screenMode, palette, imagePath,
                List.copyOf(elements), List.copyOf(texts), List.copyOf(programs));
    }

    // -------------------------------------------------------------------------
    // Elements
    // -------------------------------------------------------------------------

    private List<ResourceBank.Element> parseElements(JsonNode elementsNode,
                                                      WritableRaster raster, int planes) {
        List<ResourceBank.Element> elements = new ArrayList<>();
        if (elementsNode.isMissingNode()) return elements;
        for (JsonNode elNode : elementsNode) {
            String name = elNode.has("name") ? elNode.get("name").asText() : null;
            String type = elNode.has("type") ? elNode.get("type").asText() : null;
            List<ResourceBank.Image> images = parseImages(elNode.path("images"), raster, planes);
            elements.add(new ResourceBank.Element(name, type, images));
        }
        return elements;
    }

    private List<ResourceBank.Image> parseImages(JsonNode imagesNode,
                                                   WritableRaster raster, int planes) {
        List<ResourceBank.Image> images = new ArrayList<>();
        for (JsonNode imgNode : imagesNode) {
            int x = imgNode.get("x").asInt();
            int y = imgNode.get("y").asInt();
            int w = imgNode.get("width").asInt();
            int h = imgNode.get("height").asInt();
            int[][] pixels = extractRegion(raster, x, y, w, h);
            byte[] data = PacPicEncoder.compress(pixels, x, y, planes);
            images.add(new ResourceBank.Image(x, y, w, h, planes, data));
        }
        return images;
    }

    // -------------------------------------------------------------------------
    // Texts
    // -------------------------------------------------------------------------

    private List<String> parseTexts(JsonNode textsNode) {
        List<String> texts = new ArrayList<>();
        if (textsNode.isMissingNode()) return texts;
        for (JsonNode tn : textsNode) {
            texts.add(tn.path("text").asText());
        }
        return texts;
    }

    // -------------------------------------------------------------------------
    // Programs
    // -------------------------------------------------------------------------

    private List<String> parsePrograms(JsonNode programsNode, Path dir) throws IOException {
        List<String> programs = new ArrayList<>();
        if (programsNode.isMissingNode()) return programs;
        for (JsonNode pn : programsNode) {
            String file = pn.path("file").asText();
            programs.add(Files.readString(dir.resolve(file), java.nio.charset.StandardCharsets.UTF_8));
        }
        return programs;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static int[] parsePalette(JsonNode paletteNode) {
        int[] palette = new int[32];
        if (paletteNode.isMissingNode()) return palette;
        for (int i = 0; i < Math.min(paletteNode.size(), 32); i++) {
            palette[i] = AmigaPalette.parseHexRgb(paletteNode.get(i).asText("#000"));
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

    /** Extracts a rectangular region of palette-index pixels from a raster. */
    private static int[][] extractRegion(WritableRaster raster, int x, int y, int w, int h) {
        int[][] pixels = new int[h][w];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                pixels[row][col] = raster.getSample(x + col, y + row, 0);
            }
        }
        return pixels;
    }
}
