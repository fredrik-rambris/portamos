package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Imports a {@link SpriteBank} from a JSON metadata file previously produced by
 * {@link SpriteBankExporter}.
 *
 * <p>Usage: call {@link #importFrom(Path)} with the path to the {@code sprites.json} file.
 * Any file references in JSON (like {@code spritesheet}) are resolved relative to
 * {@code jsonPath} using {@link Path#resolveSibling(String)}.
 */
public class SpriteBankImporter {

    private static final ObjectMapper JSON = new ObjectMapper();

    /**
     * Imports a sprite/icon bank from the given JSON metadata file.
     *
     * @param jsonPath path to {@code sprites.json}
     * @return reconstructed {@link SpriteBank}
     * @throws IOException if metadata or spritesheet cannot be read
     */
    public SpriteBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());

        var bankType = parseBankType(root.path("type").asText("Sprite"));

        var spritesheetFile = root.path("spritesheet").asText("spritesheet.png");
        var spritesheetPath = jsonPath.resolveSibling(spritesheetFile);

        var sheet = ImageIO.read(spritesheetPath.toFile());
        if (sheet == null) throw new IOException("Cannot read spritesheet: " + spritesheetPath);
        if (!(sheet.getColorModel() instanceof IndexColorModel cm)) {
            throw new IllegalStateException("Spritesheet must be an indexed-colour PNG: " + spritesheetPath);
        }
        var raster = sheet.getRaster();

        var numColours = root.path("numColours").asInt(0);
        var defaultPlanes = numColours > 0 ? colorModelToPlanes(numColours) : colorModelToPlanes(cm.getMapSize());

        var palette = parsePalette(root.path("palette"));
        var sprites = parseSprites(root.path("sprites"), raster, defaultPlanes);

        return new SpriteBank(bankType, List.copyOf(sprites), palette);
    }

    // -------------------------------------------------------------------------
    // Sprites
    // -------------------------------------------------------------------------

    private List<SpriteBank.Sprite> parseSprites(JsonNode spritesNode, WritableRaster raster, int defaultPlanes) {
        var sprites = new ArrayList<SpriteBank.Sprite>();
        if (spritesNode.isMissingNode()) return sprites;

        for (var sn : spritesNode) {
            var empty = sn.path("empty").asBoolean(false);
            if (empty) {
                sprites.add(new SpriteBank.Sprite(0, 0, 0, 0, 0, new byte[0]));
                continue;
            }

            var widthWords = sn.path("widthWords").asInt(0);
            if (widthWords == 0) {
                var widthPixels = sn.path("widthPixels").asInt(0);
                widthWords = widthPixels / 16;
            }
            var height = sn.path("height").asInt(0);
            var planes = sn.path("planes").asInt(defaultPlanes);
            var hotspotX = sn.path("hotspotX").asInt(0);
            var hotspotY = sn.path("hotspotY").asInt(0);
            var x = sn.path("x").asInt(0);
            var y = sn.path("y").asInt(0);

            var data = extractPlanar(raster, x, y, widthWords, height, planes);
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
            WritableRaster raster, int x0, int y0, int widthWords, int height, int planes) {

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
                        var idx = raster.getSample(px, py, 0);
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

    private static int[] parsePalette(JsonNode paletteNode) {
        var palette = new int[32];
        if (paletteNode.isMissingNode()) return palette;

        for (int i = 0; i < Math.min(paletteNode.size(), 32); i++) {
            palette[i] = AmigaPalette.parseHexRgb(paletteNode.get(i).asText("#000"));
        }
        return palette;
    }


    private static int colorModelToPlanes(int nColors) {
        var planes = 0;
        while ((1 << planes) < nColors) planes++;
        return Math.max(planes, 1);
    }
}
