/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AmalBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        String environment,
        List<MovementRefDto> movements,
        int programCount,
        List<ProgramRefDto> programs
) implements AmosBankDto {
    public static final String TYPE = "Amal";

    /**
     * Metadata entry for one movement slot; {@code file} is absent when the movement is empty.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MovementRefDto(int index, String name, String file) {
    }

    public record ProgramRefDto(int index, String file) {
    }
}
