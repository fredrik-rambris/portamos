/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SampleBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        List<SampleDto> samples
) implements AmosBankDto {
    public static final String TYPE = "Samples";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SampleDto(
            int index,
            String name,
            int frequencyHz,
            Boolean empty,
            String file
    ) {
    }
}
