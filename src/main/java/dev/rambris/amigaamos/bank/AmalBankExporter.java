/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Exports a parsed {@link AmalBank} to an output directory.
 *
 * <p>Output files:
 * <ul>
 *   <li>{@code program_NNN.amal} — each AMAL program (ASCII); tilde ({@code ~}) line
 *       separators in the bank are replaced with newlines.</li>
 *   <li>{@code movement_NNN.json} — decoded movement data for non-empty movements (JSON).</li>
 *   <li>{@code bank.json} — metadata: bank info, movement index, program index.</li>
 * </ul>
 */
public class AmalBankExporter {


    public void export(AmalBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        exportEnvironment(bank, outDir);
        exportPrograms(bank, outDir);
        exportMovements(bank, outDir);
        exportMetadata(bank, outDir);
    }

    private void exportEnvironment(AmalBank bank, Path outDir) throws IOException {
        if (bank.environment() == null || bank.environment().isEmpty()) return;
        var dest = outDir.resolve("environment.amal");
        Files.writeString(dest, bank.environment().replace("~", "\n"), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // AMAL programs
    // -------------------------------------------------------------------------

    private void exportPrograms(AmalBank bank, Path outDir) throws IOException {
        int exported = 0;
        for (int i = 0; i < bank.programs().size(); i++) {
            var program = bank.programs().get(i);
            if (program == null || program.isEmpty()) continue;
            var dest = outDir.resolve("program_%03d.amal".formatted(i));
            // AMAL uses ~ as a line separator; convert to newlines for human-readable text
            var text = program.replace("~", "\n");
            Files.writeString(dest, text, StandardCharsets.UTF_8);
            exported++;
        }
        System.out.printf("Exported %d AMAL program(s) (of %d slots)%n",
                exported, bank.programs().size());
    }

    // -------------------------------------------------------------------------
    // Movement data
    // -------------------------------------------------------------------------

    private void exportMovements(AmalBank bank, Path outDir) throws IOException {
        int exported = 0;
        for (int i = 0; i < bank.movements().size(); i++) {
            var mov = bank.movements().get(i);
            if (mov.isEmpty()) continue;
            var dest = outDir.resolve("movement_%03d.json".formatted(i));
            var root = buildMovementJson(mov);
            JSON.writeValue(dest.toFile(), root);
            exported++;
        }
        System.out.printf("Exported %d movement(s) (of %d slots)%n",
                exported, bank.movements().size());
    }

    private void exportMovementsToMetadata(AmalBank bank, com.fasterxml.jackson.databind.node.ArrayNode node) {
        for (int i = 0; i < bank.movements().size(); i++) {
            var mov = bank.movements().get(i);
            var mn = node.addObject();
            mn.put("index", i);
            mn.put("name", mov.name());
            if (!mov.isEmpty()) {
                mn.put("file", "movement_%03d.json".formatted(i));
            }
        }
    }

    private ObjectNode buildMovementJson(AmalBank.Movement mov) {
        var root = JSON.createObjectNode();
        root.put("name", mov.name());
        if (mov.xMove() != null) {
            root.set("x", buildMovementDataJson(mov.xMove()));
        } else {
            root.putNull("x");
        }
        if (mov.yMove() != null) {
            root.set("y", buildMovementDataJson(mov.yMove()));
        } else {
            root.putNull("y");
        }
        return root;
    }

    private ObjectNode buildMovementDataJson(AmalBank.MovementData data) {
        var node = JSON.createObjectNode();
        node.put("speed", data.speed());
        var instructions = node.putArray("instructions");
        for (var inst : data.instructions()) {
            var in = instructions.addObject();
            switch (inst) {
                case AmalBank.Instruction.Wait wait -> {
                    in.put("type", "wait");
                    in.put("ticks", wait.ticks());
                }
                case AmalBank.Instruction.Delta delta -> {
                    in.put("type", "delta");
                    in.put("pixels", delta.pixels());
                }
            }
        }
        return node;
    }

    // -------------------------------------------------------------------------
    // Metadata JSON
    // -------------------------------------------------------------------------

    private void exportMetadata(AmalBank bank, Path outDir) throws IOException {
        var root = JSON.createObjectNode();
        root.put("type", "Amal");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());
        if (bank.environment() != null && !bank.environment().isEmpty()) {
            root.put("environment", "environment.amal");
        }

        exportMovementsToMetadata(bank, root.putArray("movements"));

        // programCount preserves the total slot count so reassembly matches the original.
        root.put("programCount", bank.programs().size());
        var programsNode = root.putArray("programs");
        for (int i = 0; i < bank.programs().size(); i++) {
            var prog = bank.programs().get(i);
            if (prog == null || prog.isEmpty()) continue;
            var pn = programsNode.addObject();
            pn.put("index", i);
            pn.put("file", "program_%03d.amal".formatted(i));
        }

        var dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s%n", dest);
    }
}
