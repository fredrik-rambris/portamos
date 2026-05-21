/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MenuBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        List<MenuItemDto> items
) implements AmosBankDto {
    public static final String TYPE = "Menu";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record MenuItemDto(
            /** Raw flags integer — present in legacy JSON; takes precedence over semantic fields. */
            Integer flags,
            String style,
            Boolean separate,
            Boolean inactive,
            @JsonProperty("static") Boolean isStatic,
            Boolean itemMovable,
            Integer x,
            Integer y,
            Integer keyFlag,
            Integer keyAscii,
            Integer keyScancode,
            Integer keyShift,
            String font,
            String normal,
            String selected,
            String inactiveDisplay,
            Integer pen,
            Integer paper,
            Integer outline,
            Integer penSel,
            Integer paperSel,
            Integer outlineSel,
            @JsonAlias("children") List<MenuItemDto> items
    ) {
    }
}
