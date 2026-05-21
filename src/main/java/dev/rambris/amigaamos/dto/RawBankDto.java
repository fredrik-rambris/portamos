/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record RawBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        String dataFile
) implements AmosBankDto {
    public static final String TYPE_WORK = "Work";
    public static final String TYPE_DATA = "Data";
}
