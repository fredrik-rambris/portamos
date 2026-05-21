/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.MenuBankDto;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a {@link MenuBank} to an output directory.
 *
 * <p>Produces a single {@code bank.json} containing the full menu tree.
 * Object blobs are decoded to AMOS {@code Menu$()} embedded-command strings by
 * {@link MenuObjectDecoder}. Flag bits and ink fields are decoded to named JSON properties.
 *
 * <h3>Flag bits (MnFlag high byte)</h3>
 * <pre>
 *   bit 0  Flat      – auto-set on first sibling; never stored in JSON
 *   bit 1  Fixed     – auto-derived from x/y presence; never stored in JSON
 *   bit 2  Sep       – "separate: true"
 *   bit 3  Bar       – style "bar"  (default for depth &gt; 0)
 *   bit 4  Off       – "inactive: true"
 *   bit 5  TLine     – style "tline" (default for depth 0)
 *   bit 6  TMovable  – omitted when true (default); "static: true" when false
 *   bit 7  IMovable  – "itemMovable: true" when set
 * </pre>
 *
 * <h3>Ink fields</h3>
 * <pre>
 *   pen / penSel          – MnInkA1 / MnInkA2 (default pen colour, normal / selected)
 *   paper / paperSel      – MnInkB1 / MnInkB2 (default paper colour)
 *   outline / outlineSel  – MnInkC1 / MnInkC2 (default outline colour)
 * </pre>
 * All ink fields are omitted when zero. Note these are the <em>default</em> colours used before
 * any {@code (INk)} embedded command executes; the embedded command overrides them at render time.
 *
 * <h3>Object fields</h3>
 * <pre>
 *   font            – MnObF  (font object; rarely used)
 *   normal          – MnOb1  (normal / unselected display)
 *   selected        – MnOb2  (highlighted display)
 *   inactiveDisplay – MnOb3  (greyed-out display; rarely used)
 * </pre>
 * Each is an AMOS {@code Menu$()} string (plain text plus optional {@code (XX...)} embedded
 * commands), or absent if the corresponding pointer was zero. {@code itemNumber} and the
 * runtime fields (textX/Y, maxX/Y, xx/yy, zone, adSave, datas, lData) are not stored in JSON.
 *
 * @see MenuBankImporter
 */
public class MenuBankExporter {


    // Flag-bit constants (operate on the HIGH byte of the MnFlag word)
    private static final int FL_FLAT     = 1 << 8;  // bit 0 of high byte → word bit 8
    private static final int FL_FIXED    = 1 << 9;
    private static final int FL_SEP      = 1 << 10;
    private static final int FL_BAR      = 1 << 11;
    private static final int FL_OFF      = 1 << 12;
    private static final int FL_TOTAL    = 1 << 13;
    private static final int FL_TBOUGE   = 1 << 14;
    private static final int FL_BOUGE    = 1 << 15;

    public void export(MenuBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);

        var dto = new MenuBankDto(
                MenuBankDto.TYPE,
                bank.bankNumber() & 0xFFFF,
                bank.chipRam(),
                buildItemDtos(bank.items(), 0));

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), dto);
        System.out.printf("Written %s (%d top-level items)%n", dest, bank.items().size());
    }

    private List<MenuBankDto.MenuItemDto> buildItemDtos(List<MenuNode> items, int depth) {
        return items.stream().map(node -> buildItemDto(node, depth)).toList();
    }

    private MenuBankDto.MenuItemDto buildItemDto(MenuNode node, int depth) {
        int flags = node.flags();

        boolean isBar   = (flags & FL_BAR)   != 0;
        boolean isTotal = (flags & FL_TOTAL)  != 0;
        var style = isBar ? "bar" : isTotal ? "tline" : "line";
        var defaultStyle = (depth == 0) ? "tline" : "bar";

        var children = buildItemDtos(node.children(), depth + 1);

        return new MenuBankDto.MenuItemDto(
                null,                                               // flags (never serialised)
                style.equals(defaultStyle) ? null : style,
                (flags & FL_SEP) != 0 ? true : null,
                (flags & FL_OFF) != 0 ? true : null,
                (flags & FL_TBOUGE) == 0 ? true : null,            // static = not movable
                (flags & FL_BOUGE) != 0 ? true : null,
                node.x() != 0 ? node.x() : null,
                node.y() != 0 ? node.y() : null,
                node.keyFlag() != 0 ? node.keyFlag() : null,
                node.keyAscii() != 0 ? node.keyAscii() : null,
                node.keyScancode() != 0 ? node.keyScancode() : null,
                node.keyShift() != 0 ? node.keyShift() : null,
                decoded(node.fontObject()),
                decoded(node.normalObject()),
                decoded(node.selectedObject()),
                decoded(node.inactiveObject()),
                node.inkA1() != 0 ? node.inkA1() : null,
                node.inkB1() != 0 ? node.inkB1() : null,
                node.inkC1() != 0 ? node.inkC1() : null,
                node.inkA2() != 0 ? node.inkA2() : null,
                node.inkB2() != 0 ? node.inkB2() : null,
                node.inkC2() != 0 ? node.inkC2() : null,
                children.isEmpty() ? null : children);
    }

    private static String decoded(byte[] blob) {
        return MenuObjectDecoder.decode(blob);
    }
}
