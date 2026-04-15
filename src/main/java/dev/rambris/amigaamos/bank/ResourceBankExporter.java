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
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

/**
 * Exports a parsed {@link ResourceBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code spritesheet.png} — single indexed PNG with all images composited at their
 *       original source coordinates; palette is taken from the bank header.</li>
 *   <li>{@code program_NNN.amui} — each DBL Interface program (ASCII).</li>
 *   <li>{@code resource.json} — metadata: bank info, palette, element/image list, texts.</li>
 * </ul>
 *
 * <p>Texts are embedded in {@code resource.json} only.
 */
public class ResourceBankExporter {

    private static final Pattern FILNAME_PATTERN = Pattern.compile(".*[/\\\\:](?<base>[^/\\\\:].+?)(\\.(?<ext>[^.]+))?$");
    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void export(ResourceBank bank, Path outDir) throws IOException {
        export(bank, outDir, false);
    }

    /**
     * Exports the resource bank to {@code outDir}.
     *
     * @param bank   the resource bank to export
     * @param outDir target directory
     * @param ilbm   if {@code true}, write the spritesheet as an IFF ILBM; otherwise PNG
     * @throws IOException if any file cannot be written
     */
    public void export(ResourceBank bank, Path outDir, boolean ilbm) throws IOException {
        var ext = ilbm ? "iff" : "png";
        var spritesheetFilename = normalizeFilename(bank.imagePath(), ext);
        if (spritesheetFilename == null) spritesheetFilename = "spritesheet." + ext;
        Files.createDirectories(outDir);
        exportSpriteSheet(bank, outDir.resolve(spritesheetFilename), ilbm ? "IFF" : "PNG");
        exportPrograms(bank, outDir);
        exportMetadata(bank, outDir, spritesheetFilename);
    }

    private String normalizeFilename(String filename, String ext) {
        String result = null;

        var m = FILNAME_PATTERN.matcher(filename);

        if(m.matches()) {
            result = m.group("base") + "." + ext;
        }

        return result;
    }

    // -------------------------------------------------------------------------
    // Sprite sheet
    // -------------------------------------------------------------------------

    private void exportSpriteSheet(ResourceBank bank, Path dest, String format) throws IOException {
        // Compute bounding box of all images
        int sheetW = 0, sheetH = 0;
        int maxColour = 0;
        for (var el : bank.elements()) {
            for (var img : el.images()) {
                sheetW = Math.max(sheetW, img.x() + img.width());
                sheetH = Math.max(sheetH, img.y() + img.height());
                maxColour = Math.max(maxColour, 1 << img.planes());
            }
        }

        if (sheetW == 0 || sheetH == 0) {
            System.out.println("No images to export.");
            return;
        }


        var colorModel = AmigaPalette.buildIndexColorModel(bank.palette(), maxColour);

        var sheet = new BufferedImage(sheetW, sheetH,
                BufferedImage.TYPE_BYTE_INDEXED, colorModel);
        var raster = sheet.getRaster();

        int decoded = 0, errors = 0;
        for (var el : bank.elements()) {
            for (var img : el.images()) {
                try {
                    var pixels = PacPicDecoder.decompress(img.data());
                    int imgH = pixels.length;
                    int imgW = imgH > 0 ? pixels[0].length : 0;
                    for (int y = 0; y < imgH; y++) {
                        for (int x = 0; x < imgW; x++) {
                            int px = img.x() + x;
                            int py = img.y() + y;
                            if (px < sheetW && py < sheetH) {
                                raster.setSample(px, py, 0, pixels[y][x]);
                            }
                        }
                    }
                    decoded++;
                } catch (Exception e) {
                    System.err.printf("Warning: failed to decode image at (%d,%d): %s%n",
                            img.x(), img.y(), e.getMessage());
                    errors++;
                }
            }
        }

        ImageIO.write(sheet, format, dest.toFile());
        System.out.printf("Sprite sheet: %dx%d px, %d images decoded%s → %s%n",
                sheetW, sheetH, decoded,
                errors > 0 ? " (" + errors + " errors)" : "",
                dest);
    }

    // -------------------------------------------------------------------------
    // Interface programs
    // -------------------------------------------------------------------------

    private void exportPrograms(ResourceBank bank, Path outDir) throws IOException {
        for (int i = 0; i < bank.programs().size(); i++) {
            var dest = outDir.resolve("program_%03d.amui".formatted(i));
            Files.writeString(dest, bank.programs().get(i), java.nio.charset.StandardCharsets.UTF_8);
        }
        System.out.printf("Exported %d interface program(s)%n", bank.programs().size());
    }

    // -------------------------------------------------------------------------
    // Metadata JSON
    // -------------------------------------------------------------------------

    private void exportMetadata(ResourceBank bank, Path outDir, String spritesheetFilename) throws IOException {
        var root = JSON.createObjectNode();
        root.put("type", "Resource");
        root.put("bankNumber", bank.bankNumber());
        root.put("chipRam", bank.chipRam());
        root.put("screenMode", "0x%04X".formatted(bank.screenMode()));
        root.put("imagePath", bank.imagePath());
        root.put("spritesheet", spritesheetFilename);
        root.put("numColours", bank.getNumCols());

        var paletteNode = root.putArray("palette");

        for (int color : bank.palette()) {
            paletteNode.add(AmigaPalette.toHexRgb(color));
        }

        var elementsNode = root.putArray("elements");
        for (var el : bank.elements()) {
            var en = elementsNode.addObject();
            if (el.name() != null) en.put("name", el.name());
            if (el.type() != null) en.put("type", el.type());

            var imagesNode = en.putArray("images");
            for (var img : el.images()) {
                var imgNode = imagesNode.addObject();
                imgNode.put("x", img.x());
                imgNode.put("y", img.y());
                imgNode.put("width", img.width());
                imgNode.put("height", img.height());
            }
        }

        var textsNode = root.putArray("texts");
        for (int i = 0; i < bank.texts().size(); i++) {
            var tn = textsNode.addObject();
            tn.put("index", i);
            tn.put("text", bank.texts().get(i));
        }

        var programsNode = root.putArray("programs");
        for (int i = 0; i < bank.programs().size(); i++) {
            var pn = programsNode.addObject();
            pn.put("index", i);
            pn.put("file", "program_%03d.amui".formatted(i));
        }

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s%n", dest);
    }
}
