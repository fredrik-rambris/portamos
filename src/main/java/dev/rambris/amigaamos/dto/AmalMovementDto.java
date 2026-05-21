/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * DTO for individual {@code movement_NNN.json} files.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AmalMovementDto(
        String name,
        MovementDataDto x,
        MovementDataDto y
) {
    public record MovementDataDto(int speed, List<InstructionDto> instructions) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstructionDto(String type, Integer ticks, Integer pixels) {
    }
}
