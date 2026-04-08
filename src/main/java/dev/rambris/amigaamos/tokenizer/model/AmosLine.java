/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer.model;

import java.util.List;

/**
 * One tokenized line of AMOS source code.
 *
 * @param indent AMOS indent level (1-based: 1 = top level, 2 = one indent, …)
 * @param tokens the tokens on this line, not including the implicit EOL marker
 */
public record AmosLine(int indent, List<AmosToken> tokens) {

    public AmosLine {
        tokens = List.copyOf(tokens);
    }
}
