/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * In-memory model of an AMOS Professional Menu bank ({@code AmBk / "Menu    "}).
 *
 * <p>A Menu bank stores the full definition of an AMOS menu system — the same data that
 * {@code Menu To Bank} serialises and {@code Bank To Menu} restores at runtime.
 *
 * <p>The bank payload is a DFS-preorder sequence of 70-byte {@link MenuNode} structs, with
 * object blobs appended immediately after each node. Top-level menu-bar items are chained via
 * {@code MnNext}; children of each item are reached via {@code MnLat}. In this model both chains
 * are flattened:
 * <ul>
 *   <li>{@link #items()} — the top-level title bar items (the {@code MnNext} chain from the root)</li>
 *   <li>{@link MenuNode#children()} — the sub-items for each node</li>
 * </ul>
 */
public record MenuBank(
        short bankNumber,
        boolean chipRam,
        List<MenuNode> items
) implements AmosBank {

    @Override
    public Type type() { return Type.MENU; }

    @Override
    public BankWriter writer() { return new MenuBankWriter(); }
}
