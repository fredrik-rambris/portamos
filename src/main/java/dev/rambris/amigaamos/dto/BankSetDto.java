/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import java.util.List;

/**
 * JSON descriptor for a Bank Set ({@code .Abs}) assembled from individual {@code .Abk} files.
 *
 * <p>Example:
 * <pre>
 * {
 *   "type":  "BankSet",
 *   "banks": [
 *     "explosion-1-data-9.abk",
 *     "explosion-2-samples-5.abk"
 *   ]
 * }
 * </pre>
 *
 * <p>File paths in {@code banks} are relative to the JSON file's parent directory.
 */
public record BankSetDto(String type, List<String> banks) {
    public static final String TYPE = "BankSet";
}
