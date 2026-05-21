/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record PacPicBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        @JsonAlias("pngFile") String imageFile,
        int srcX,
        int srcY,
        int planes,
        boolean spack,
        ScreenHeaderDto screen
) implements AmosBankDto {
    public static final String TYPE = "PacPic";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ScreenHeaderDto(
            int width,
            int height,
            int hardX,
            int hardY,
            int displayWidth,
            int displayHeight,
            int offsetX,
            int offsetY,
            int bplCon0,
            int numColors,
            int numPlanes,
            List<String> palette
    ) {
    }
}
