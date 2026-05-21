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
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports a {@link MenuBank} from a JSON metadata file previously produced by
 * {@link MenuBankExporter}.
 *
 * <p>Reconstructs the {@code MnFlag} word from named JSON properties:
 * <ul>
 *   <li>{@code style} – "bar" (default depth&gt;0) | "tline" (default depth=0) | "line"</li>
 *   <li>{@code separate} – boolean, default false (Menu Separate)</li>
 *   <li>{@code inactive} – boolean, default false (Menu Inactive)</li>
 *   <li>{@code static} – boolean, default false; true = not user-draggable (Menu Static)</li>
 *   <li>{@code itemMovable} – boolean, default false (Menu Item Movable)</li>
 * </ul>
 *
 * <p>Structural flag bits are auto-derived:
 * <ul>
 *   <li>{@code Flat} (bit 0) – set on the first sibling at each level</li>
 *   <li>{@code Fixed} (bit 1) – set when {@code x} or {@code y} is present</li>
 * </ul>
 *
 * <p>Item numbers are generated sequentially (1-based) within each sibling group.
 *
 * <p>Runtime fields (textX/Y, maxX/Y, xx/yy, zone, adSave, datas, lData) are always
 * written as 0; AMOS recalculates them via {@code Menu Calc} at runtime.
 */
public class MenuBankImporter {


    // Flag-bit masks (high byte of the MnFlag word)
    private static final int FL_FLAT     = 1 << 8;
    private static final int FL_FIXED    = 1 << 9;
    private static final int FL_SEP      = 1 << 10;
    private static final int FL_BAR      = 1 << 11;
    private static final int FL_OFF      = 1 << 12;
    private static final int FL_TOTAL    = 1 << 13;
    private static final int FL_TBOUGE   = 1 << 14;
    private static final int FL_BOUGE    = 1 << 15;

    public MenuBank importFrom(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), MenuBankDto.class);

        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 1);
        var chipRam = dto.chipRam() != null && dto.chipRam();

        var items = readItems(dto.items(), jsonPath.getParent(), 0);

        return new MenuBank(bankNumber, chipRam, List.copyOf(items));
    }

    private static List<MenuNode> readItems(
            List<MenuBankDto.MenuItemDto> dtos, Path dir, int depth) throws IOException {
        var result = new ArrayList<MenuNode>();
        if (dtos == null) return result;
        int idx = 0;
        for (var d : dtos) {
            result.add(readNode(d, dir, depth, idx == 0, idx + 1));
            idx++;
        }
        return result;
    }

    private static MenuNode readNode(
            MenuBankDto.MenuItemDto d, Path dir, int depth, boolean firstInGroup,
            int itemNumber) throws IOException {

        // ── flags ──────────────────────────────────────────────────────────────
        int flags;
        if (d.flags() != null) {
            // Legacy JSON with a raw flags integer — use it directly.
            flags = d.flags();
        } else {
            flags = reconstructFlags(d, depth, firstInGroup);
        }

        // ── position ────────────────────────────────────────────────────────────
        int x = d.x() != null ? d.x() : 0;
        int y = d.y() != null ? d.y() : 0;

        // ── keyboard shortcut ───────────────────────────────────────────────────
        int keyFlag = d.keyFlag() != null ? d.keyFlag() : 0;
        int keyAscii = d.keyAscii() != null ? d.keyAscii() : 0;
        int keyScancode = d.keyScancode() != null ? d.keyScancode() : 0;
        int keyShift = d.keyShift() != null ? d.keyShift() : 0;

        // ── display objects ─────────────────────────────────────────────────────
        var fontObject = readBlob(d.font(), dir);
        var normalObject = readBlob(d.normal(), dir);
        var selectedObject = readBlob(d.selected(), dir);
        var inactiveObject = readBlob(d.inactiveDisplay(), dir);

        // ── inks ────────────────────────────────────────────────────────────────
        int inkA1 = d.pen() != null ? d.pen() : 0;
        int inkB1 = d.paper() != null ? d.paper() : 0;
        int inkC1 = d.outline() != null ? d.outline() : 0;
        int inkA2 = d.penSel() != null ? d.penSel() : 0;
        int inkB2 = d.paperSel() != null ? d.paperSel() : 0;
        int inkC2 = d.outlineSel() != null ? d.outlineSel() : 0;

        // ── children ────────────────────────────────────────────────────────────
        var children = readItems(d.items(), dir, depth + 1);

        return new MenuNode(
                itemNumber, flags,
                x, y, 0, 0, 0, 0, 0, 0,
                0,
                keyFlag, keyAscii, keyScancode, keyShift,
                fontObject, normalObject, selectedObject, inactiveObject,
                0, 0, 0,
                inkA1, inkB1, inkC1,
                inkA2, inkB2, inkC2,
                children
        );
    }

    // -------------------------------------------------------------------------
    // Flag reconstruction
    // -------------------------------------------------------------------------

    private static int reconstructFlags(MenuBankDto.MenuItemDto d, int depth, boolean firstInGroup) {
        int flags = 0;

        // Structural bits – auto-derived
        if (firstInGroup) flags |= FL_FLAT;
        if ((d.x() != null && d.x() != 0)
            || (d.y() != null && d.y() != 0)) flags |= FL_FIXED;

        // Style: "bar" | "line" | "tline"; default depends on depth
        var defaultStyle = (depth == 0) ? "tline" : "bar";
        var style = d.style() != null ? d.style() : defaultStyle;
        switch (style) {
            case "bar"   -> flags |= FL_BAR;
            case "tline" -> flags |= FL_TOTAL;
            // "line" → neither bar nor total
        }

        // Behaviour bits
        if (Boolean.TRUE.equals(d.separate())) flags |= FL_SEP;
        if (Boolean.TRUE.equals(d.inactive())) flags |= FL_OFF;
        if (!Boolean.TRUE.equals(d.isStatic())) flags |= FL_TBOUGE; // movable by default
        if (Boolean.TRUE.equals(d.itemMovable())) flags |= FL_BOUGE;

        return flags;
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /**
     * Loads a blob from a string field: if the value ends with {@code .bin} it is read as a
     * raw file; otherwise it is treated as an embedded-command string and encoded by
     * {@link MenuObjectEncoder}.
     */
    private static byte[] readBlob(String value, Path dir) throws IOException {
        if (value == null || value.isEmpty()) return null;

        if (value.endsWith(".bin")) {
            var blobPath = dir.resolve(value);
            if (!Files.exists(blobPath)) {
                throw new IOException("Object blob not found: " + blobPath);
            }
            var data = Files.readAllBytes(blobPath);
            if (data.length < 2) {
                throw new IOException("Object blob too short: " + blobPath);
            }
            return data;
        }

        return MenuObjectEncoder.encode(value);
    }
}
