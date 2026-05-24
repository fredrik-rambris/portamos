/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record SpriteBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        String spritesheet,
        int numColours,
        List<String> palette,
        List<SpriteDto> sprites
) implements AmosBankDto {
    public static final String TYPE_SPRITE = "Sprite";
    public static final String TYPE_ICON = "Icon";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SpriteDto(
            int index,
            Boolean empty,
            Integer x,
            Integer y,
            Integer width,
            Integer height,
            Integer planes,
            Integer hotspotX,
            Integer hotspotY
    ) {
    }
}
