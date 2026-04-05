package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    /**
     * Exports the sprite bank to {@code outDir}, creating the directory if needed.
     *
     * @param bank   the sprite bank to export
     * @param outDir target directory
     * @throws IOException if any file cannot be written
     */
    public void export(SpriteBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        var spritesheetName = "spritesheet.png";
        exportSpritesheet(bank, outDir.resolve(spritesheetName));
        exportMetadata(bank, outDir, spritesheetName);
    }

    // -------------------------------------------------------------------------
    // Spritesheet
    // -------------------------------------------------------------------------

    private void exportSpritesheet(SpriteBank bank, Path dest) throws IOException {
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
        var colorModel = AmigaPalette.buildIndexColorModel(bank.palette(), maxColors);
        var sheet = new BufferedImage(sheetW, sheetH, BufferedImage.TYPE_BYTE_INDEXED, colorModel);
        var raster = sheet.getRaster();

        int x = 0;
        for (var sprite : bank.sprites()) {
            if (sprite.isEmpty()) continue;
            var pixels = toIndexedPixels(sprite);
            for (int py = 0; py < sprite.height(); py++) {
                for (int px = 0; px < sprite.widthPixels(); px++) {
                    raster.setSample(x + px, py, 0, pixels[py][px]);
                }
            }
            x += sprite.widthPixels();
        }

        ImageIO.write(sheet, "PNG", dest.toFile());
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

    private void exportMetadata(SpriteBank bank, Path outDir, String spritesheetName)
            throws IOException {
        var root = JSON.createObjectNode();
        root.put("type", bank.type() == AmosBank.Type.ICONS ? "Icon" : "Sprite");
        root.put("spritesheet", spritesheetName);

        // Determine max colour count for numColours field
        var maxPlanes = bank.sprites().stream()
                .filter(s -> !s.isEmpty())
                .mapToInt(SpriteBank.Sprite::planes)
                .max().orElse(0);
        root.put("numColours", maxPlanes > 0 ? 1 << maxPlanes : 0);

        ArrayNode paletteNode = root.putArray("palette");
        for (var color : bank.palette()) {
            paletteNode.add(AmigaPalette.toHexRgb(color));
        }

        ArrayNode spritesNode = root.putArray("sprites");
        int sheetX = 0;
        for (int i = 0; i < bank.sprites().size(); i++) {
            var sprite = bank.sprites().get(i);
            var sn = spritesNode.addObject();
            sn.put("index", i);
            if (sprite.isEmpty()) {
                sn.put("empty", true);
            } else {
                sn.put("x", sheetX);
                sn.put("widthPixels", sprite.widthPixels());
                sn.put("widthWords", sprite.widthWords());
                sn.put("height", sprite.height());
                sn.put("planes", sprite.planes());
                sn.put("hotspotX", sprite.hotspotX());
                sn.put("hotspotY", sprite.hotspotY());
                sheetX += sprite.widthPixels();
            }
        }

        var dest = outDir.resolve("sprites.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s%n", dest);
    }
}


