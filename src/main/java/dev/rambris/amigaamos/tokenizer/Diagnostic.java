/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

public record Diagnostic(int line, int column, Severity severity, String message) {

    public enum Severity {ERROR, WARNING}

    public Diagnostic(int line, Severity severity, String message) {
        this(line, -1, severity, message);
    }

    public String format(String filename) {
        String loc = column >= 1 ? line + ":" + column : String.valueOf(line);
        return filename + ":" + loc + ": " + severity.name().toLowerCase() + ": " + message;
    }
}
