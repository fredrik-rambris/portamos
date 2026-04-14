/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * One node in an AMOS Professional menu tree.
 *
 * <p>AMOS stores menus as a tree of 70-byte ({@code MnLong}) nodes. Sibling items at the same
 * level are connected via {@code MnNext}; the first child of an item is reached via {@code MnLat}.
 * In this Java model both chains are flattened: siblings of the top-level bar become
 * {@link MenuBank#items()}, and every node carries its children directly in
 * {@link #children()}.
 *
 * <h3>Binary struct layout ({@code MnLong}, 70 bytes, all big-endian)</h3>
 * <pre>
 *   +0  MnPrev   long  – absolute Amiga address at save time; always 0 on write
 *   +4  MnNext   long  – relative byte offset to next sibling (0 = none)
 *   +8  MnLat    long  – relative byte offset to first child (0 = none)
 *   +12 MnNb     word  – item number, 1-based within its sibling group
 *   +14 MnFlag   word  – display / state flags (see below)
 *   +16 MnX      word  – x position (set by Set Menu; 0 = auto)
 *   +18 MnY      word  – y position (set by Set Menu; 0 = auto)
 *   +20 MnTx     word  – text x  (runtime; recalculated by Menu Calc)
 *   +22 MnTy     word  – text y  (runtime; recalculated by Menu Calc)
 *   +24 MnMX     word  – max x   (runtime; recalculated by Menu Calc)
 *   +26 MnMY     word  – max y   (runtime; recalculated by Menu Calc)
 *   +28 MnXX     word  – xx      (runtime; recalculated by Menu Calc)
 *   +30 MnYY     word  – yy      (runtime; recalculated by Menu Calc)
 *   +32 MnZone   word  – Intuition zone number (runtime)
 *   +34 MnKFlag  byte  – keyboard-shortcut flag
 *   +35 MnKAsc   byte  – keyboard-shortcut ASCII code
 *   +36 MnKSc    byte  – keyboard-shortcut scancode
 *   +37 MnKSh    byte  – keyboard-shortcut shift mask
 *   +38 MnObF    long  – relative offset to font object blob (0 = none)
 *   +42 MnOb1    long  – relative offset to normal display blob (0 = none)
 *   +46 MnOb2    long  – relative offset to selected display blob (0 = none)
 *   +50 MnOb3    long  – relative offset to inactive display blob (0 = none)
 *   +54 MnAdSave long  – saved screen address (runtime only)
 *   +58 MnDatas  long  – local data area pointer (runtime; set by REserve embedded cmd)
 *   +62 MnLData  word  – local data area length  (runtime; set by REserve embedded cmd)
 *   +64 MnInkA1  byte  – default pen   colour, normal state
 *   +65 MnInkB1  byte  – default paper colour, normal state
 *   +66 MnInkC1  byte  – default outline colour, normal state
 *   +67 MnInkA2  byte  – default pen   colour, selected state
 *   +68 MnInkB2  byte  – default paper colour, selected state
 *   +69 MnInkC2  byte  – default outline colour, selected state
 * </pre>
 *
 * <h3>{@code MnFlag} bit layout</h3>
 * <p>AMOS's 68k code uses {@code btst #n, MnFlag(a2)}, which operates on the <em>high byte</em>
 * (first byte in memory) of the word. As a Java {@code short} read big-endian the masks are:
 * <pre>
 *   0x0100  MnFlat  : set on the first node of each lateral (child) chain; auto-derived on write
 *   0x0200  MnFixed : position set explicitly by Set Menu; auto-derived from x/y on write
 *   0x0400  MnSep   : Menu Separate was applied (items are independent)
 *   0x0800  MnBar   : display as vertical bar (default for sub-items)
 *   0x1000  MnOff   : item is inactive (Menu Inactive)
 *   0x2000  MnTotal : display as total line spanning full width (default for top-level)
 *   0x4000  MnTBouge: title bar is user-draggable (Menu Movable; default on)
 *   0x8000  MnBouge : individual item is user-draggable (Menu Item Movable; default off)
 * </pre>
 * <p>The low byte is a runtime "called-once" flag ({@code Menu Called} / {@code Menu Once});
 * it is always 0 when saved.
 *
 * <h3>Object blobs</h3>
 * <p>Each blob begins with a big-endian {@code uint16} giving the total byte length (including
 * those two bytes), followed by a sequence of 16-bit command codes and their parameters.
 * The command set is decoded/encoded by {@link MenuObjectDecoder} / {@link MenuObjectEncoder}.
 * A {@code null} value means the corresponding object pointer was zero in the binary.
 */
public record MenuNode(
        int itemNumber,
        int flags,
        int x,
        int y,
        int textX,
        int textY,
        int maxX,
        int maxY,
        int xx,
        int yy,
        int zone,
        int keyFlag,
        int keyAscii,
        int keyScancode,
        int keyShift,
        byte[] fontObject,
        byte[] normalObject,
        byte[] selectedObject,
        byte[] inactiveObject,
        int adSave,
        int datas,
        int lData,
        int inkA1,
        int inkB1,
        int inkC1,
        int inkA2,
        int inkB2,
        int inkC2,
        List<MenuNode> children
) {}
