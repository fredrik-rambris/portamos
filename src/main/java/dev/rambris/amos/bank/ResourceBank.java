package dev.rambris.amos.bank;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.awt.image.WritableRaster;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

/**
 * In-memory model of a parsed AMOS Professional Resource Bank.
 *
 * <p>The images section contains a flat list of <em>elements</em>.  Each element is one
 * entry in the binary offset table and corresponds to one of two formats:
 * <ul>
 *   <li><b>Simple image</b> (Image_NN in the spec): entry starts with the Pac.Pic magic
 *       {@code 0x06071963}.  One image, {@code type} and {@code name} are null.</li>
 *   <li><b>Named element</b> (Res_NN in the spec): entry starts with an 8-byte ASCII name,
 *       followed by a 2-byte image count, 2-byte {@code 0xABCD} marker, then one or more
 *       consecutive Pac.Pic images.  {@code type} is inferred from the count:
 *       1 → SINGLE, 3 → LINE, 9 → BOX.</li>
 * </ul>
 */
public record ResourceBank(
        short bankNumber,
        boolean chipRam,
        int screenMode,
        int[] palette,        // 32 entries, 12-bit Amiga colour each
        String imagePath,     // source IFF path stored in the bank header
        List<Element> elements,
        List<String> texts,
        List<String> programs // DBL Interface programs (.amui)
) implements AmosBank {

    @Override
    public Type type() {
        return Type.RESOURCE;
    }

    /**
     * One entry in the image offset table.
     *
     * <p>For simple images {@code name} and {@code type} are {@code null} and
     * {@code images} has exactly one entry.
     * For named elements they are set and {@code images} may have 1, 3, or 9 entries.
     */
    public record Element(
            String name,          // null for simple images; ≤8 chars for named elements
            String type,          // null | "SINGLE" | "LINE" | "BOX"
            List<Image> images
    ) {}

    /** One Pac.Pic image within an element. */
    public record Image(
            int x,                // source X in pixels
            int y,                // source Y in pixels
            int width,            // width in pixels
            int height,           // height in pixels  (height_in_y × height_in_lines)
            int planes,           // number of bitplanes
            byte[] data           // raw Pac.Pic bytes, starting with 0x06071963
    ) {}

    public int getNumCols() {
        return elements.stream().flatMap(e -> e.images.stream()).mapToInt(i -> 1 << i.planes).max().orElse(palette.length);
    }

    // =========================================================================
    // Builder API
    // =========================================================================

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing a {@link ResourceBank} programmatically.
     *
     * <p>Typical usage:
     * <pre>{@code
     * ResourceBank bank = ResourceBank.builder()
     *     .bankNumber((short) 1)
     *     .addText("Hello")
     *     .image(spritesheetPath, ec -> {
     *         ec.image((short) 0,  (short) 0,  (short) 16, (short) 16);
     *         ec.box(  (short) 16, (short) 0,  (short) 8,  (short) 8);
     *     })
     *     .build();
     * }</pre>
     */
    public static final class Builder {
        private short bankNumber = 1;
        private boolean chipRam = true;
        private int screenMode = 0;
        private int[] palette = new int[32];
        private String imagePath = "";
        private final List<Element> elements = new ArrayList<>();
        private final List<String> texts = new ArrayList<>();
        private final List<String> programs = new ArrayList<>();

        private Builder() {
        }

        public Builder bankNumber(short n) {
            bankNumber = n;
            return this;
        }

        public Builder chipRam(boolean c) {
            chipRam = c;
            return this;
        }

        public Builder screenMode(int m) {
            screenMode = m;
            return this;
        }

        public Builder imagePath(String p) {
            imagePath = p;
            return this;
        }

        public Builder palette(int[] p) {
            palette = Arrays.copyOf(p, 32);
            return this;
        }

        public Builder addText(String t) {
            texts.add(t);
            return this;
        }

        public Builder addProgram(String p) {
            programs.add(p);
            return this;
        }

        /**
         * Loads the given indexed PNG sprite sheet and extracts elements from it as
         * configured by the {@link ElementConfig} consumer.
         *
         * <p>The palette and {@code imagePath} are derived from the sprite sheet on the
         * first call (if not already set explicitly via {@link #palette} / {@link #imagePath}).
         * The number of bitplanes is computed from the PNG's {@link IndexColorModel} map size.
         *
         * @param spritesheet path to an indexed-colour PNG
         * @param cfg         element configuration callback
         * @throws IOException              if the image file cannot be read
         * @throws IllegalArgumentException if any coordinate or dimension is not a multiple of 8
         * @throws IllegalStateException    if the PNG is not indexed-colour
         */
        public Builder image(Path spritesheet, Consumer<ElementConfig> cfg) throws IOException {
            BufferedImage img = ImageIO.read(spritesheet.toFile());
            if (img == null) throw new IOException("Cannot read image: " + spritesheet);
            if (!(img.getColorModel() instanceof IndexColorModel cm)) {
                throw new IllegalStateException("Sprite sheet must be an indexed-colour PNG: " + spritesheet);
            }

            // Derive bitplanes from the colour model map size
            int nColors = cm.getMapSize();
            int planes = 0;
            while ((1 << planes) < nColors) planes++;
            if (planes == 0) planes = 1;

            // Populate palette and imagePath on first call (if not set explicitly)
            if (Arrays.equals(palette, new int[32])) {
                for (int i = 0; i < Math.min(nColors, 32); i++) {
                    int r = Math.round(cm.getRed(i) / 17.0f) & 0xF;
                    int g = Math.round(cm.getGreen(i) / 17.0f) & 0xF;
                    int b = Math.round(cm.getBlue(i) / 17.0f) & 0xF;
                    palette[i] = (r << 8) | (g << 4) | b;
                }
            }
            if (imagePath.isEmpty()) {
                imagePath = spritesheet.getFileName().toString();
            }

            WritableRaster raster = img.getRaster();

            ElementConfig ec = new ElementConfig();
            cfg.accept(ec);

            for (ElementConfig.Spec spec : ec.specs()) {
                switch (spec.kind()) {
                    case IMAGE -> {
                        int[][] px = extractRegion(raster, spec.x(), spec.y(), spec.w(), spec.h());
                        byte[] data = PacPicEncoder.compress(px, spec.x(), spec.y(), planes);
                        elements.add(new Element(null, null,
                                List.of(new Image(spec.x(), spec.y(), spec.w(), spec.h(), planes, data))));
                    }
                    case BOX -> {
                        List<Image> imgs = new ArrayList<>(9);
                        for (int row = 0; row < 3; row++) {
                            for (int col = 0; col < 3; col++) {
                                int ix = spec.x() + col * spec.w();
                                int iy = spec.y() + row * spec.h();
                                int[][] px = extractRegion(raster, ix, iy, spec.w(), spec.h());
                                byte[] data = PacPicEncoder.compress(px, ix, iy, planes);
                                imgs.add(new Image(ix, iy, spec.w(), spec.h(), planes, data));
                            }
                        }
                        elements.add(new Element(null, "BOX", imgs));
                    }
                    case HLINE -> {
                        List<Image> imgs = new ArrayList<>(3);
                        for (int col = 0; col < 3; col++) {
                            int ix = spec.x() + col * spec.w();
                            int[][] px = extractRegion(raster, ix, spec.y(), spec.w(), spec.h());
                            byte[] data = PacPicEncoder.compress(px, ix, spec.y(), planes);
                            imgs.add(new Image(ix, spec.y(), spec.w(), spec.h(), planes, data));
                        }
                        elements.add(new Element(null, "HLINE", imgs));
                    }
                    case VLINE -> {
                        List<Image> imgs = new ArrayList<>(3);
                        for (int row = 0; row < 3; row++) {
                            int iy = spec.y() + row * spec.h();
                            int[][] px = extractRegion(raster, spec.x(), iy, spec.w(), spec.h());
                            byte[] data = PacPicEncoder.compress(px, spec.x(), iy, planes);
                            imgs.add(new Image(spec.x(), iy, spec.w(), spec.h(), planes, data));
                        }
                        elements.add(new Element(null, "VLINE", imgs));
                    }
                }
            }
            return this;
        }

        public ResourceBank build() {
            return new ResourceBank(bankNumber, chipRam, screenMode,
                    Arrays.copyOf(palette, 32), imagePath,
                    List.copyOf(elements), List.copyOf(texts), List.copyOf(programs));
        }

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

    // =========================================================================
    // ElementConfig DSL
    // =========================================================================

    /**
     * Configuration DSL for specifying which regions of a sprite sheet to extract
     * as {@link Element}s.  Passed as a {@link Consumer} to {@link Builder#image}.
     *
     * <p>All {@code x} and {@code w} values must be divisible by 8 (Amiga byte boundary).
     *
     * <ul>
     *   <li>{@link #image} — one ungrouped image</li>
     *   <li>{@link #box}   — 3×3 grid of 9 images (BOX group)</li>
     *   <li>{@link #hline} — 3 images side by side (HLINE group)</li>
     *   <li>{@link #vline} — 3 images stacked (VLINE group)</li>
     * </ul>
     *
     * <p>For grouped types (box / hline / vline) {@code x,y} is the top-left corner of
     * the first tile and {@code w,h} is the size of <em>one</em> tile.
     */
    public static final class ElementConfig {

        enum Kind {IMAGE, BOX, HLINE, VLINE}

        record Spec(Kind kind, int x, int y, int w, int h) {
        }

        private final List<Spec> specs = new ArrayList<>();

        private ElementConfig() {
        }

        /**
         * Adds a single ungrouped image at {@code (x,y)} of size {@code w×h}.
         */
        public ElementConfig image(short x, short y, short w, short h) {
            validate(x, w);
            specs.add(new Spec(Kind.IMAGE, x & 0xFFFF, y & 0xFFFF, w & 0xFFFF, h & 0xFFFF));
            return this;
        }

        /**
         * Adds a BOX group: 9 images in a 3×3 grid.
         * {@code x,y} = top-left of tile [0,0]; {@code w,h} = size of one tile.
         */
        public ElementConfig box(short x, short y, short w, short h) {
            validate(x, w);
            specs.add(new Spec(Kind.BOX, x & 0xFFFF, y & 0xFFFF, w & 0xFFFF, h & 0xFFFF));
            return this;
        }

        /**
         * Adds an HLINE group: 3 images arranged horizontally.
         * {@code x,y} = top-left of first tile; {@code w,h} = size of one tile.
         */
        public ElementConfig hline(short x, short y, short w, short h) {
            validate(x, w);
            specs.add(new Spec(Kind.HLINE, x & 0xFFFF, y & 0xFFFF, w & 0xFFFF, h & 0xFFFF));
            return this;
        }

        /**
         * Adds a VLINE group: 3 images arranged vertically.
         * {@code x,y} = top-left of first tile; {@code w,h} = size of one tile.
         */
        public ElementConfig vline(short x, short y, short w, short h) {
            validate(x, w);
            specs.add(new Spec(Kind.VLINE, x & 0xFFFF, y & 0xFFFF, w & 0xFFFF, h & 0xFFFF));
            return this;
        }

        List<Spec> specs() {
            return specs;
        }

        private static void validate(short x, short w) {
            if ((x & 7) != 0) throw new IllegalArgumentException("x must be divisible by 8, got: " + (x & 0xFFFF));
            if ((w & 7) != 0) throw new IllegalArgumentException("w must be divisible by 8, got: " + (w & 0xFFFF));
        }
    }
}
