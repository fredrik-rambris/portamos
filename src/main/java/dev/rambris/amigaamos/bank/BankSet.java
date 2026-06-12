/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import java.util.List;

/**
 * An AMOS Professional Bank Set (.Abs file): a group of banks stored together
 * under the {@code AmBs} envelope.
 *
 * <p>File format:
 * <pre>
 *   dc.b  "AmBs"
 *   dc.w  Number_Of_Banks
 *   [ bank data, concatenated ]
 * </pre>
 */
public record BankSet(List<AmosBank> banks) {
}
