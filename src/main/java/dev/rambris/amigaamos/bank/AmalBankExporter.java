/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

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

    private static final ObjectMapper JSON = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    public void export(AmalBank bank, Path outDir) throws IOException {
        Files.createDirectories(outDir);
        exportPrograms(bank, outDir);
        exportMovements(bank, outDir);
        exportMetadata(bank, outDir);
    }

    // -------------------------------------------------------------------------
    // AMAL programs
    // -------------------------------------------------------------------------

    private void exportPrograms(AmalBank bank, Path outDir) throws IOException {
        int exported = 0;
        for (int i = 0; i < bank.programs().size(); i++) {
            String program = bank.programs().get(i);
            if (program == null || program.isEmpty()) continue;
            Path dest = outDir.resolve("program_%03d.amal".formatted(i));
            // AMAL uses ~ as a line separator; convert to newlines for human-readable text
            String text = program.replace("~", "\n");
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
            AmalBank.Movement mov = bank.movements().get(i);
            if (mov.isEmpty()) continue;
            Path dest = outDir.resolve("movement_%03d.json".formatted(i));
            ObjectNode root = buildMovementJson(mov);
            JSON.writeValue(dest.toFile(), root);
            exported++;
        }
        System.out.printf("Exported %d movement(s) (of %d slots)%n",
                exported, bank.movements().size());
    }

    private ObjectNode buildMovementJson(AmalBank.Movement mov) {
        ObjectNode root = JSON.createObjectNode();
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
        ObjectNode node = JSON.createObjectNode();
        node.put("speed", data.speed());
        ArrayNode instructions = node.putArray("instructions");
        for (AmalBank.Instruction inst : data.instructions()) {
            ObjectNode in = instructions.addObject();
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
        ObjectNode root = JSON.createObjectNode();
        root.put("type", "Amal");
        root.put("bankNumber", bank.bankNumber() & 0xFFFF);
        root.put("chipRam", bank.chipRam());

        ArrayNode movementsNode = root.putArray("movements");
        for (int i = 0; i < bank.movements().size(); i++) {
            AmalBank.Movement mov = bank.movements().get(i);
            ObjectNode mn = movementsNode.addObject();
            mn.put("index", i);
            mn.put("name", mov.name());
            mn.put("empty", mov.isEmpty());
            if (!mov.isEmpty()) {
                mn.put("file", "movement_%03d.json".formatted(i));
            }
        }

        ArrayNode programsNode = root.putArray("programs");
        for (int i = 0; i < bank.programs().size(); i++) {
            String prog = bank.programs().get(i);
            ObjectNode pn = programsNode.addObject();
            pn.put("index", i);
            boolean hasContent = prog != null && !prog.isEmpty();
            pn.put("empty", !hasContent);
            if (hasContent) {
                pn.put("file", "program_%03d.amal".formatted(i));
            }
        }

        Path dest = outDir.resolve("bank.json");
        JSON.writeValue(dest.toFile(), root);
        System.out.printf("Written %s%n", dest);
    }
}
