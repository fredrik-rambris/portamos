/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record MusicBankDto(
        String type,
        Integer bankNumber,
        Boolean chipRam,
        List<InstrumentDto> instruments,
        List<SongDto> songs,
        List<PatternDto> patterns
) implements AmosBankDto {
    public static final String TYPE = "Music";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record InstrumentDto(
            String name,
            int volume,
            Integer totalLength,
            Integer loopStart,
            Integer loopLength,
            String sample
    ) {
    }

    /**
     * {@code name} and {@code tempo} are omitted when empty / zero.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record SongDto(
            String name,
            Integer tempo,
            List<List<Integer>> sequence
    ) {
    }

    public record PatternDto(List<List<VoiceItemDto>> voices) {
    }

    /**
     * Either a note entry ({@code period} + {@code duration}) or a command entry
     * ({@code command} + optional {@code parameter}).
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record VoiceItemDto(
            String command,
            Integer parameter,
            Integer period,
            Integer duration
    ) {
    }
}
