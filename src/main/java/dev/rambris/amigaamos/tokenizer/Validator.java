/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.tokenizer;

import dev.rambris.amigaamos.tokenizer.model.AmosFile;
import dev.rambris.amigaamos.tokenizer.model.AmosToken;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.*;

/**
 * Validates an ASCII AMOS source file without producing a binary.
 * <p>
 * Checks performed:
 * - Block structure: For/Next, Repeat/Until, While/Wend, Do/Loop,
 * If/Else If/Else/End If, Procedure/End Proc
 * <p>
 * Returns a list of {@link Diagnostic} objects sorted by line number.
 * An empty list means the file is valid.
 */
public class Validator {

    private static final int TOK_FOR = 0x023C;
    private static final int TOK_NEXT = 0x0246;
    private static final int TOK_REPEAT = 0x0250;
    private static final int TOK_UNTIL = 0x025C;
    private static final int TOK_WHILE = 0x0268;
    private static final int TOK_WEND = 0x0274;
    private static final int TOK_DO = 0x027E;
    private static final int TOK_LOOP = 0x0286;
    private static final int TOK_IF = 0x02BE;
    private static final int TOK_THEN = 0x02C6;
    private static final int TOK_ELSE = 0x02D0;
    private static final int TOK_END_IF = 0x02DA;
    private static final int TOK_ELSE_IF = 0x25A4;
    private static final int TOK_PROCEDURE = 0x0376;
    private static final int TOK_END_PROC = 0x0390;

    private record StackEntry(String keyword, int lineNum) {
    }

    private final Tokenizer tokenizer;

    public Validator() {
        tokenizer = new Tokenizer();
    }

    public Validator withDefinition(Path path) {
        tokenizer.withDefinition(path);
        return this;
    }

    public Validator withoutDefinition(String id) {
        tokenizer.withoutDefinition(id);
        return this;
    }

    public List<Diagnostic> validate(Path path, Charset charset) throws IOException {
        var diagnostics = new ArrayList<Diagnostic>();
        AmosFile file;
        try {
            file = tokenizer.parse(path, charset);
        } catch (TokenizeException e) {
            String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            diagnostics.add(new Diagnostic(e.lineNumber(), e.column(), Diagnostic.Severity.ERROR, msg));
            return diagnostics;
        }
        checkBlocks(file, diagnostics);
        return diagnostics;
    }

    public List<Diagnostic> validate(Path path) throws IOException {
        return validate(path, StandardCharsets.UTF_8);
    }

    private void checkBlocks(AmosFile file, List<Diagnostic> diagnostics) {
        var stack = new ArrayDeque<StackEntry>();
        var lines = file.lines();

        for (int i = 0; i < lines.size(); i++) {
            int lineNum = i + 1;
            var tokens = lines.get(i).tokens();

            boolean hasFor = false, hasNext = false;
            boolean hasRepeat = false, hasUntil = false;
            boolean hasWhile = false, hasWend = false;
            boolean hasDo = false, hasLoop = false;
            boolean hasIf = false, hasThen = false;
            boolean hasElse = false, hasElseIf = false, hasEndIf = false;
            boolean hasProcedure = false, hasEndProc = false;

            for (var tok : tokens) {
                if (tok instanceof AmosToken.Keyword k) {
                    switch (k.value()) {
                        case TOK_FOR -> hasFor = true;
                        case TOK_NEXT -> hasNext = true;
                        case TOK_REPEAT -> hasRepeat = true;
                        case TOK_UNTIL -> hasUntil = true;
                        case TOK_WHILE -> hasWhile = true;
                        case TOK_WEND -> hasWend = true;
                        case TOK_DO -> hasDo = true;
                        case TOK_LOOP -> hasLoop = true;
                        case TOK_IF -> hasIf = true;
                        case TOK_THEN -> hasThen = true;
                        case TOK_ELSE -> hasElse = true;
                        case TOK_ELSE_IF -> hasElseIf = true;
                        case TOK_END_IF -> hasEndIf = true;
                        case TOK_PROCEDURE -> hasProcedure = true;
                        case TOK_END_PROC -> hasEndProc = true;
                    }
                }
            }

            // Openers
            if (hasFor) stack.push(new StackEntry("For", lineNum));
            if (hasRepeat) stack.push(new StackEntry("Repeat", lineNum));
            if (hasWhile) stack.push(new StackEntry("While", lineNum));
            if (hasDo) stack.push(new StackEntry("Do", lineNum));
            if (hasProcedure) stack.push(new StackEntry("Procedure", lineNum));

            // Multiline If (If without Then on same line = block if)
            if (hasIf && !hasThen) stack.push(new StackEntry("If", lineNum));

            // Inline If-Then-Else on a single line: Else is part of the inline construct.
            boolean inlineIfElse = hasIf && hasThen && (hasElse || hasElseIf);

            // Else If / Else: close the current branch, open a new one
            if (!inlineIfElse && hasElseIf) {
                if (!stack.isEmpty() && isIfBranch(stack.peek().keyword())) {
                    stack.pop();
                } else {
                    diagnostics.add(new Diagnostic(lineNum, Diagnostic.Severity.ERROR,
                            "'Else If' without matching 'If'"));
                }
                stack.push(new StackEntry("Else If", lineNum));
            } else if (!inlineIfElse && hasElse) {
                if (!stack.isEmpty() && isIfBranch(stack.peek().keyword())) {
                    stack.pop();
                } else {
                    diagnostics.add(new Diagnostic(lineNum, Diagnostic.Severity.ERROR,
                            "'Else' without matching 'If'"));
                }
                stack.push(new StackEntry("Else", lineNum));
            }

            // Closers
            if (hasNext) closeBlock(stack, "For", "Next", lineNum, diagnostics);
            if (hasUntil) closeBlock(stack, "Repeat", "Until", lineNum, diagnostics);
            if (hasWend) closeBlock(stack, "While", "Wend", lineNum, diagnostics);
            if (hasLoop) closeBlock(stack, "Do", "Loop", lineNum, diagnostics);
            if (hasEndIf) {
                if (!stack.isEmpty() && isIfBranch(stack.peek().keyword())) {
                    stack.pop();
                } else {
                    diagnostics.add(new Diagnostic(lineNum, Diagnostic.Severity.ERROR,
                            "'End If' without matching 'If'"));
                }
            }
            if (hasEndProc) closeBlock(stack, "Procedure", "End Proc", lineNum, diagnostics);
        }

        // Anything left on the stack was never closed
        while (!stack.isEmpty()) {
            var entry = stack.pop();
            diagnostics.add(new Diagnostic(entry.lineNum(), Diagnostic.Severity.ERROR,
                    "'" + entry.keyword() + "' is never closed"));
        }

        diagnostics.sort(Comparator.comparingInt(Diagnostic::line));
    }

    private static boolean isIfBranch(String keyword) {
        return keyword.equals("If") || keyword.equals("Else If") || keyword.equals("Else");
    }

    private static void closeBlock(Deque<StackEntry> stack, String opener, String closer,
                                   int lineNum, List<Diagnostic> diagnostics) {
        if (stack.isEmpty()) {
            diagnostics.add(new Diagnostic(lineNum, Diagnostic.Severity.ERROR,
                    "'" + closer + "' without matching '" + opener + "'"));
            return;
        }
        var top = stack.peek();
        if (top.keyword().equals(opener)) {
            stack.pop();
        } else {
            diagnostics.add(new Diagnostic(lineNum, Diagnostic.Severity.ERROR,
                    "'" + closer + "' does not match '" + top.keyword()
                    + "' opened at line " + top.lineNum()));
        }
    }
}
