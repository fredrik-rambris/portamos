/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer.model;

public sealed interface AmosToken permits
        AmosToken.SingleQuoteRem,
        AmosToken.Rem,
        AmosToken.DoubleQuoteString,
        AmosToken.SingleQuoteString,
        AmosToken.DecimalInt,
        AmosToken.HexInt,
        AmosToken.BinaryInt,
        AmosToken.Flt,
        AmosToken.Dbl,
        AmosToken.Variable,
        AmosToken.Label,
        AmosToken.ProcRef,
        AmosToken.LabelRef,
        AmosToken.Keyword,
        AmosToken.ExtKeyword {

    /** Single-quote REM comment, token = $0652 */
    record SingleQuoteRem(String text) implements AmosToken {}

    /** Rem keyword comment, token = $064A */
    record Rem(String text) implements AmosToken {}

    /** Double-quoted string literal, token = $0026 */
    record DoubleQuoteString(String text) implements AmosToken {}

    /** Single-quoted string literal, token = $002E */
    record SingleQuoteString(String text) implements AmosToken {}

    /** Decimal integer literal, token = $003E */
    record DecimalInt(int value) implements AmosToken {}

    /** Hex integer literal, token = $0036 */
    record HexInt(int value) implements AmosToken {}

    /** Binary integer literal, token = $001E */
    record BinaryInt(int value) implements AmosToken {}

    /** Single-precision float, token = $0046 (custom AMOS float format) */
    record Flt(float value) implements AmosToken {}

    /** Double-precision float, token = $2B6A */
    record Dbl(double value) implements AmosToken {}

    /** Variable reference, token = $0006 */
    record Variable(String name, VarType type, boolean isArray, int extraFlags) implements AmosToken {
        /** Convenience constructor for non-array variables with no extra flags. */
        public Variable(String name, VarType type) { this(name, type, false, 0); }
        /** Convenience constructor for non-array variables. */
        public Variable(String name, VarType type, boolean isArray) { this(name, type, isArray, 0); }
    }

    /** Label definition, token = $000C */
    record Label(String name) implements AmosToken {}

    /** Procedure call reference, token = $0012 */
    record ProcRef(String name) implements AmosToken {}

    /** Label goto reference, token = $0018 */
    record LabelRef(String name) implements AmosToken {}

    /** Regular keyword (just the 2-byte token value) */
    record Keyword(int value) implements AmosToken {}

    /** Extension command, token = $004E */
    record ExtKeyword(int slot, int offset) implements AmosToken {}

    enum VarType {
        INTEGER, FLOAT, STRING
    }
}
