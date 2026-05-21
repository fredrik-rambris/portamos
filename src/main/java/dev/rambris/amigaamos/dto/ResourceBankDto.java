/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ResourceBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        String screenMode,
        String imagePath,
        String spritesheet,
        int numColours,
        List<String> palette,
        List<ElementDto> elements,
        List<TextDto> texts,
        List<ProgramDto> programs
) implements AmosBankDto {
    public static final String TYPE = "Resource";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ElementDto(String name, String type, List<ImageDto> images) {
    }

    public record ImageDto(int x, int y, int width, int height) {
    }

    public record TextDto(int index, String text) {
    }

    public record ProgramDto(int index, String file) {
    }
}
