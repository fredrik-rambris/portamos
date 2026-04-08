/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SpriteBankTest {

    private static final Path SPRITES_ABK = Path.of("src/test/resources/Sprites.Abk");
    private static final Path ICONS_ABK   = Path.of("src/test/resources/Icons.Abk");

    @Test
    void readSpriteCount() throws Exception {
        var bank = SpriteBankReader.read(SPRITES_ABK);
        assertEquals(8, bank.sprites().size(), "sprite count");
    }

    @Test
    void readViGenericDispatch() throws Exception {
        var bank = AmosBank.read(SPRITES_ABK);
        assertInstanceOf(SpriteBank.class, bank);
        assertEquals(AmosBank.Type.SPRITES, bank.type());
        assertEquals(1, bank.bankNumber());
        assertTrue(bank.chipRam());
    }

    // -------------------------------------------------------------------------
    // Icon bank (AmIc)
    // -------------------------------------------------------------------------

    @Test
    void readIconCount() throws Exception {
        var bank = SpriteBankReader.read(ICONS_ABK);
        assertEquals(1, bank.sprites().size(), "icon count");
    }

    @Test
    void readIconViaGenericDispatch() throws Exception {
        var bank = AmosBank.read(ICONS_ABK);
        assertInstanceOf(SpriteBank.class, bank);
        assertEquals(AmosBank.Type.ICONS, bank.type());
        assertEquals(2, bank.bankNumber(), "icon banks are bank 2");
        assertTrue(bank.chipRam());
    }

    @Test
    void iconGeometry() throws Exception {
        var bank = SpriteBankReader.read(ICONS_ABK);
        var icon = bank.sprites().getFirst();
        assertEquals(3, icon.widthWords(),  "width in words");
        assertEquals(48, icon.widthPixels(), "width in pixels");
        assertEquals(64, icon.height(),     "height");
        assertEquals(4,  icon.planes(),     "planes");
        assertEquals(3 * 2 * 64 * 4, icon.data().length, "data size");
    }

    @Test
    void iconRoundTrip() throws Exception {
        var original = SpriteBankReader.read(ICONS_ABK);
        var bytes = new SpriteBankWriter().toBytes(original);
        // Magic must be preserved as AmIc
        assertEquals("AmIc", new String(bytes, 0, 4));
        var readback = SpriteBankReader.read(bytes);
        assertEquals(AmosBank.Type.ICONS, readback.type());
        assertEquals(original.sprites().size(), readback.sprites().size());
        assertArrayEquals(original.palette(), readback.palette());
        var oi = original.sprites().getFirst();
        var ri = readback.sprites().getFirst();
        assertEquals(oi.widthWords(), ri.widthWords());
        assertEquals(oi.height(),     ri.height());
        assertEquals(oi.planes(),     ri.planes());
        assertArrayEquals(oi.data(),  ri.data());
    }

    @Test
    void exportIcons(@TempDir Path tmp) throws Exception {
        var bank = SpriteBankReader.read(ICONS_ABK);
        new SpriteBankExporter().export(bank, tmp);
        assertTrue(tmp.resolve("spritesheet.png").toFile().exists(), "spritesheet.png");
        assertTrue(tmp.resolve("sprites.json").toFile().exists(),    "sprites.json");
    }

    @Test
    void spriteGeometry() throws Exception {
        var bank = SpriteBankReader.read(SPRITES_ABK);
        var sprites = bank.sprites();

        // Sprite 0: 1 word wide, 16 px tall, 2 planes
        assertEquals(1, sprites.get(0).widthWords());
        assertEquals(16, sprites.get(0).height());
        assertEquals(2, sprites.get(0).planes());
        assertEquals(1 * 2 * 16 * 2, sprites.get(0).data().length);

        // Sprite 1: 1 word wide, 16 px tall, 4 planes
        assertEquals(1, sprites.get(1).widthWords());
        assertEquals(16, sprites.get(1).height());
        assertEquals(4, sprites.get(1).planes());

        // Sprite 5: 4 words wide (64 px), 64 px tall, 4 planes
        assertEquals(4, sprites.get(5).widthWords());
        assertEquals(64, sprites.get(5).height());
        assertEquals(4, sprites.get(5).planes());
        assertEquals(4 * 2 * 64 * 4, sprites.get(5).data().length);
    }

    @Test
    void paletteHas32Entries() throws Exception {
        var bank = SpriteBankReader.read(SPRITES_ABK);
        assertEquals(32, bank.palette().length);
        // First entry is black
        assertEquals(0x000, bank.palette()[0]);
    }

    @Test
    void roundTrip() throws Exception {
        var original = SpriteBankReader.read(SPRITES_ABK);
        var writer = new SpriteBankWriter();
        var bytes = writer.toBytes(original);
        var readback = SpriteBankReader.read(bytes);

        assertEquals(original.sprites().size(), readback.sprites().size());
        assertArrayEquals(original.palette(), readback.palette());

        for (int i = 0; i < original.sprites().size(); i++) {
            var os = original.sprites().get(i);
            var rs = readback.sprites().get(i);
            assertEquals(os.widthWords(), rs.widthWords(), "sprite[" + i + "].widthWords");
            assertEquals(os.height(),     rs.height(),     "sprite[" + i + "].height");
            assertEquals(os.planes(),     rs.planes(),     "sprite[" + i + "].planes");
            assertEquals(os.hotspotX(),   rs.hotspotX(),   "sprite[" + i + "].hotspotX");
            assertEquals(os.hotspotY(),   rs.hotspotY(),   "sprite[" + i + "].hotspotY");
            assertArrayEquals(os.data(),  rs.data(),       "sprite[" + i + "].data");
        }
    }

    @Test
    void export(@TempDir Path tmp) throws Exception {
        var bank = SpriteBankReader.read(SPRITES_ABK);
        new SpriteBankExporter().export(bank, tmp);

        assertTrue(tmp.resolve("spritesheet.png").toFile().exists(), "spritesheet.png");
        assertTrue(tmp.resolve("sprites.json").toFile().exists(),    "sprites.json");
    }

    @Test
    void importExportRoundTripSprites(@TempDir Path tmp) throws Exception {
        var original = SpriteBankReader.read(SPRITES_ABK);

        var exportDir = tmp.resolve("sprites-export");
        new SpriteBankExporter().export(original, exportDir);

        var imported = new SpriteBankImporter().importFrom(exportDir.resolve("sprites.json"));

        assertEquals(original.type(), imported.type());
        assertEquals(original.sprites().size(), imported.sprites().size());
        assertArrayEquals(original.palette(), imported.palette());

        for (int i = 0; i < original.sprites().size(); i++) {
            var os = original.sprites().get(i);
            var is = imported.sprites().get(i);
            assertEquals(os.widthWords(), is.widthWords(), "sprite[" + i + "].widthWords");
            assertEquals(os.height(), is.height(), "sprite[" + i + "].height");
            assertEquals(os.planes(), is.planes(), "sprite[" + i + "].planes");
            assertEquals(os.hotspotX(), is.hotspotX(), "sprite[" + i + "].hotspotX");
            assertEquals(os.hotspotY(), is.hotspotY(), "sprite[" + i + "].hotspotY");
            assertArrayEquals(os.data(), is.data(), "sprite[" + i + "].data");
        }
    }

    @Test
    void importExportRoundTripIcons(@TempDir Path tmp) throws Exception {
        var original = SpriteBankReader.read(ICONS_ABK);

        var exportDir = tmp.resolve("icons-export");
        new SpriteBankExporter().export(original, exportDir);

        var imported = new SpriteBankImporter().importFrom(exportDir.resolve("sprites.json"));

        assertEquals(AmosBank.Type.ICONS, imported.type());
        assertEquals(2, imported.bankNumber());
        assertEquals(original.sprites().size(), imported.sprites().size());
        assertArrayEquals(original.palette(), imported.palette());

        var oi = original.sprites().getFirst();
        var ii = imported.sprites().getFirst();
        assertEquals(oi.widthWords(), ii.widthWords());
        assertEquals(oi.height(), ii.height());
        assertEquals(oi.planes(), ii.planes());
        assertEquals(oi.hotspotX(), ii.hotspotX());
        assertEquals(oi.hotspotY(), ii.hotspotY());
        assertArrayEquals(oi.data(), ii.data());
    }
}




