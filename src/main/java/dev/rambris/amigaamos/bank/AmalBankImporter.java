/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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
 *     { "index": 0, "empty": false, "file": "program_000.amal" },
 *     { "index": 1, "empty": true  },
 *     ...
 *   ]
 * }
 * </pre>
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

    private static final ObjectMapper JSON = new ObjectMapper();

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
        var result = new ArrayList<AmalBank.Movement>();
        if (movementsNode.isMissingNode()) return result;

        for (var mn : movementsNode) {
            var name = mn.path("name").asText("Empty");
            boolean empty = mn.path("empty").asBoolean(true);
            if (empty || !mn.has("file")) {
                result.add(new AmalBank.Movement(name, null, null));
                continue;
            }

            var movFile = dir.resolve(mn.get("file").asText());
            var movRoot = JSON.readTree(movFile.toFile());

            var xMove = parseMovementData(movRoot.path("x"));
            var yMove = parseMovementData(movRoot.path("y"));

            result.add(new AmalBank.Movement(name, xMove, yMove));
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
        var result = new ArrayList<String>();
        if (programsNode.isMissingNode()) return result;

        for (var pn : programsNode) {
            boolean empty = pn.path("empty").asBoolean(true);
            if (empty || !pn.has("file")) {
                result.add("");
                continue;
            }
            var progFile = dir.resolve(pn.get("file").asText());
            // Convert newlines back to AMAL's ~ line separator
            var text = Files.readString(progFile, StandardCharsets.UTF_8)
                               .replace("\r\n", "~")
                               .replace("\n", "~");
            result.add(text);
        }
        return result;
    }
}
