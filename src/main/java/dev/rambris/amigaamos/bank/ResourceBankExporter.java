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
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import static dev.rambris.amigaamos.JsonConfig.JSON;

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


    public void export(ResourceBank bank, Path jsonPath) throws IOException {
        export(bank, jsonPath, false);
    }

    /**
     * Exports the resource bank to {@code jsonPath}.
     *
     * @param bank     the resource bank to export
     * @param jsonPath destination JSON metadata file; data files are written as siblings
     * @param ilbm     if {@code true}, write the spritesheet as an IFF ILBM; otherwise PNG
     * @throws IOException if any file cannot be written
     */
    public void export(ResourceBank bank, Path jsonPath, boolean ilbm) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        var ext = ilbm ? "iff" : "png";
        var spritesheetFilename = normalizeFilename(bank.imagePath(), ext);
        if (spritesheetFilename == null) spritesheetFilename = stem + "." + ext;
        Files.createDirectories(dir);
        exportSpriteSheet(bank, dir.resolve(spritesheetFilename), ilbm);
        exportPrograms(bank, dir, stem);
        exportMetadata(bank, jsonPath, dir, stem, spritesheetFilename);
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

    private void exportSpriteSheet(ResourceBank bank, Path dest, boolean ilbm) throws IOException {
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

        var allPixels = new int[sheetH][sheetW];
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
                                allPixels[py][px] = pixels[y][x];
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

        if (ilbm) {
            int planes = Integer.numberOfTrailingZeros(maxColour);
            IndexedPngWriter.writeIlbm(bank.palette(), planes, allPixels, sheetW, sheetH, dest);
        } else {
            IndexedPngWriter.writePng(bank.palette(), maxColour, allPixels, sheetW, sheetH, dest);
        }
        System.out.printf("Sprite sheet: %dx%d px, %d images decoded%s → %s%n",
                sheetW, sheetH, decoded,
                errors > 0 ? " (" + errors + " errors)" : "",
                dest);
    }

    // -------------------------------------------------------------------------
    // Interface programs
    // -------------------------------------------------------------------------

    private void exportPrograms(ResourceBank bank, Path dir, String stem) throws IOException {
        for (int i = 0; i < bank.programs().size(); i++) {
            var dest = dir.resolve(stem + "-program%03d.amui".formatted(i));
            Files.writeString(dest, bank.programs().get(i), java.nio.charset.StandardCharsets.UTF_8);
        }
        System.out.printf("Exported %d interface program(s)%n", bank.programs().size());
    }

    // -------------------------------------------------------------------------
    // Metadata JSON
    // -------------------------------------------------------------------------

    private void exportMetadata(ResourceBank bank, Path jsonPath, Path dir, String stem,
            String spritesheetFilename) throws IOException {
        var paletteList = Arrays.stream(bank.palette())
                .mapToObj(AmigaPalette::toHexRgb)
                .toList();

        var elementDtos = new ArrayList<ResourceBankDto.ElementDto>();
        for (var el : bank.elements()) {
            var imageDtos = el.images().stream()
                    .map(img -> new ResourceBankDto.ImageDto(img.x(), img.y(), img.width(), img.height()))
                    .toList();
            elementDtos.add(new ResourceBankDto.ElementDto(el.name(), el.type(), imageDtos));
        }

        var textDtos = new ArrayList<ResourceBankDto.TextDto>();
        for (int i = 0; i < bank.texts().size(); i++) {
            textDtos.add(new ResourceBankDto.TextDto(i, bank.texts().get(i)));
        }

        var programDtos = new ArrayList<ResourceBankDto.ProgramDto>();
        for (int i = 0; i < bank.programs().size(); i++) {
            programDtos.add(new ResourceBankDto.ProgramDto(i, stem + "-program%03d.amui".formatted(i)));
        }

        var dto = new ResourceBankDto(
                ResourceBankDto.TYPE,
                (int) bank.bankNumber(),
                bank.chipRam(),
                "0x%04X".formatted(bank.screenMode()),
                bank.imagePath(),
                spritesheetFilename,
                bank.getNumCols(),
                paletteList,
                List.copyOf(elementDtos),
                List.copyOf(textDtos),
                List.copyOf(programDtos));

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s%n", jsonPath);
    }
}
