/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Imports an {@link AmalBank} from a JSON metadata file previously produced by
 * {@link AmalBankExporter}.
 *
 * <p>Usage: call {@link #importFrom(Path)} with the path to the {@code bank.json} file.
 * Referenced movement and program files are resolved as siblings of the JSON file.
 *
 * <p>Expected {@code bank.json} structure:
 * <pre>
 * {
 *   "type":       "Amal",
 *   "bankNumber": 4,
 *   "chipRam":    false,
 *   "movements": [
 *     { "index": 0, "name": "Move 1", "empty": false, "file": "movement_000.json" },
 *     { "index": 1, "name": "Empty",  "empty": true  },
 *     ...
 *   ],
 *   "programs": [
 *     { "index": 3, "file": "program_003.amal" },
 *     ...
 *   ]
 * }
 * </pre>
 *
 * <p>Only non-empty programs appear in the {@code programs} array. The total slot count is
 * derived from {@code max(index) + 1}. Empty trailing slots are dropped.
 *
 * <p>Each referenced {@code movement_NNN.json} has the structure produced by
 * {@link AmalBankExporter}:
 * <pre>
 * {
 *   "name": "Move 1",
 *   "x": { "speed": 1, "instructions": [ {"type":"wait","ticks":11}, ... ] },
 *   "y": null
 * }
 * </pre>
 *
 * <p>Each referenced {@code program_NNN.amal} is plain text with newlines; newlines
 * are converted back to {@code ~} for storage.
 */
public class AmalBankImporter {

    /**
     * Imports an {@link AmalBank} from the given {@code bank.json} metadata file.
     *
     * @param jsonPath path to the {@code bank.json} file
     * @return the reconstructed in-memory bank
     * @throws IOException if any referenced file cannot be read or the JSON is malformed
     */
    public AmalBank importFrom(Path jsonPath) throws IOException {
        var root = JSON.readTree(jsonPath.toFile());
        var dir = jsonPath.toAbsolutePath().getParent();

        var bankNumber = (short) root.path("bankNumber").asInt(1);
        var chipRam  = root.path("chipRam").asBoolean(false);

        var movements = parseMovements(root.path("movements"), dir);
        var programs = parsePrograms(root.path("programs"), dir);

        return new AmalBank(bankNumber, chipRam, List.copyOf(movements), List.copyOf(programs));
    }

    // -------------------------------------------------------------------------
    // Movements
    // -------------------------------------------------------------------------

    private List<AmalBank.Movement> parseMovements(JsonNode movementsNode, Path dir) throws IOException {
        if (movementsNode.isMissingNode()) return List.of();
        var result = new ArrayList<AmalBank.Movement>();
        int counter = 0;
        for (var mn : movementsNode) {
            int index = mn.has("index") ? mn.path("index").asInt() : counter;
            counter = index + 1;
            while (result.size() <= index) result.add(new AmalBank.Movement("", null, null));
            if (!mn.has("file")) continue;
            var movRoot = JSON.readTree(dir.resolve(mn.get("file").asText()).toFile());
            result.set(index, new AmalBank.Movement(
                    movRoot.path("name").asText(""),
                    parseMovementData(movRoot.path("x")),
                    parseMovementData(movRoot.path("y"))));
        }
        return result;
    }

    private AmalBank.MovementData parseMovementData(JsonNode node) {
        if (node.isNull() || node.isMissingNode()) return null;
        int speed = node.path("speed").asInt(1);
        var instructions = new ArrayList<AmalBank.Instruction>();
        for (var inst : node.path("instructions")) {
            var type = inst.path("type").asText();
            instructions.add(switch (type) {
                case "wait"  -> new AmalBank.Instruction.Wait(inst.path("ticks").asInt());
                case "delta" -> new AmalBank.Instruction.Delta(inst.path("pixels").asInt());
                default -> throw new IllegalArgumentException("Unknown instruction type: " + type);
            });
        }
        return new AmalBank.MovementData(speed, List.copyOf(instructions));
    }

    // -------------------------------------------------------------------------
    // Programs
    // -------------------------------------------------------------------------

    private List<String> parsePrograms(JsonNode programsNode, Path dir) throws IOException {
        if (programsNode.isMissingNode()) return List.of();
        var result = new ArrayList<String>();
        int counter = 0;
        for (var pn : programsNode) {
            int index = pn.has("index") ? pn.path("index").asInt() : counter;
            counter = index + 1;
            while (result.size() <= index) result.add("");
            if (pn.has("file")) {
                result.set(index, readProgramFile(dir.resolve(pn.get("file").asText())));
            }
        }
        return result;
    }

    private String readProgramFile(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "~")
                .replace("\n", "~");
    }
}
