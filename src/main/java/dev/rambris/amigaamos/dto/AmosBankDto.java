/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.dto;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Polymorphic root for all bank JSON descriptors.
 *
 * <p>The {@code "type"} field already present in every bank JSON file acts as the
 * type discriminator. Use {@code visible = true} so the value is still forwarded
 * to the concrete record constructor (records require every component to be set).
 *
 * <p>Usage:
 * <pre>{@code
 * AmosBankDto dto = JSON.readValue(bankJsonFile, AmosBankDto.class);
 * switch (dto) {
 *     case MusicBankDto music -> ...
 *     case SampleBankDto sample -> ...
 *     ...
 * }
 * }</pre>
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AmalBankDto.class, name = AmalBankDto.TYPE),
        @JsonSubTypes.Type(value = MenuBankDto.class, name = MenuBankDto.TYPE),
        @JsonSubTypes.Type(value = MusicBankDto.class, name = MusicBankDto.TYPE),
        @JsonSubTypes.Type(value = PacPicBankDto.class, name = PacPicBankDto.TYPE),
        @JsonSubTypes.Type(value = RawBankDto.class, name = RawBankDto.TYPE_WORK),
        @JsonSubTypes.Type(value = RawBankDto.class, name = RawBankDto.TYPE_DATA),
        @JsonSubTypes.Type(value = ResourceBankDto.class, name = ResourceBankDto.TYPE),
        @JsonSubTypes.Type(value = SampleBankDto.class, name = SampleBankDto.TYPE),
        @JsonSubTypes.Type(value = SpriteBankDto.class, name = SpriteBankDto.TYPE_SPRITE),
        @JsonSubTypes.Type(value = SpriteBankDto.class, name = SpriteBankDto.TYPE_ICON),
        @JsonSubTypes.Type(value = TrackerBankDto.class, name = TrackerBankDto.TYPE),
})
public interface AmosBankDto {
    String type();

    Integer bankNumber();

    Boolean chipRam();
}
