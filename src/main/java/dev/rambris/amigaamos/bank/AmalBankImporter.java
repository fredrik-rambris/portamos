/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.AmalBankDto;
import dev.rambris.amigaamos.dto.AmalMovementDto;

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
 *     { "index": 0, "name": "Move 1", "file": "movement_000.json" },
 *     { "index": 1, "name": "Empty"  },
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
 * taken from {@code programCount}. Empty trailing slots are dropped.
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
        var dto = JSON.readValue(jsonPath.toFile(), AmalBankDto.class);
        var dir = jsonPath.toAbsolutePath().getParent();

        var bankNumber = (short) (dto.bankNumber() != null ? dto.bankNumber() : 1);
        var chipRam = dto.chipRam() != null && dto.chipRam();

        var movements = parseMovements(dto.movements(), dir);
        var programs = parsePrograms(dto.programs(), dto.programCount(), dir);
        var environment = parseEnvironment(dto.environment(), dir);

        return new AmalBank(bankNumber, chipRam, List.copyOf(movements), List.copyOf(programs), environment);
    }

    // -------------------------------------------------------------------------
    // Movements
    // -------------------------------------------------------------------------

    private List<AmalBank.Movement> parseMovements(
            List<AmalBankDto.MovementRefDto> refs, Path dir) throws IOException {
        if (refs == null) return List.of();
        var result = new ArrayList<AmalBank.Movement>();
        int counter = 0;
        for (var ref : refs) {
            int index = ref.index();
            counter = index + 1;
            while (result.size() <= index) result.add(new AmalBank.Movement("", null, null));
            String name = ref.name() != null ? ref.name() : "";
            if (ref.file() != null) {
                var movDto = JSON.readValue(dir.resolve(ref.file()).toFile(), AmalMovementDto.class);
                result.set(index, new AmalBank.Movement(
                        movDto.name() != null ? movDto.name() : name,
                        toMovementData(movDto.x(), true),
                        toMovementData(movDto.y(), false)));
            } else {
                result.set(index, new AmalBank.Movement(name, null, null));
            }
        }
        return result;
    }

    private AmalBank.MovementData toMovementData(AmalMovementDto.MovementDataDto dto, boolean isX) {
        if (dto == null) return null;
        var instructions = new ArrayList<AmalBank.Instruction>();
        if (dto.instructions() != null) {
            for (var inst : dto.instructions()) {
                instructions.add(switch (inst.type()) {
                    case "wait" -> new AmalBank.Instruction.Wait(
                            inst.ticks() != null ? inst.ticks() : 0);
                    case "delta" -> new AmalBank.Instruction.Delta(
                            inst.pixels() != null ? inst.pixels() : 0);
                    default -> throw new IllegalArgumentException("Unknown instruction type: " + inst.type());
                });
            }
        }
        return isX
                ? AmalBank.MovementData.fromXInstructions(dto.speed(), List.copyOf(instructions))
                : AmalBank.MovementData.fromYInstructions(dto.speed(), List.copyOf(instructions));
    }

    // -------------------------------------------------------------------------
    // Programs
    // -------------------------------------------------------------------------

    private List<String> parsePrograms(
            List<AmalBankDto.ProgramRefDto> refs, int programCount, Path dir) throws IOException {
        var result = new ArrayList<String>();
        if (refs != null) {
            int counter = 0;
            for (var ref : refs) {
                int index = ref.index();
                counter = index + 1;
                while (result.size() <= index) result.add("");
                if (ref.file() != null) {
                    result.set(index, readProgramFile(dir.resolve(ref.file())));
                }
            }
        }
        // Pad to programCount to preserve total slot count.
        while (result.size() < programCount) result.add("");
        return result;
    }

    private String readProgramFile(Path file) throws IOException {
        return Files.readString(file, StandardCharsets.UTF_8)
                .replace("\r\n", "~")
                .replace("\n", "~");
    }

    // -------------------------------------------------------------------------
    // Environment
    // -------------------------------------------------------------------------

    private String parseEnvironment(String envFilename, Path dir) throws IOException {
        if (envFilename == null || envFilename.isBlank()) return "";
        var file = dir.resolve(envFilename);
        if (!Files.exists(file)) return "";
        return readProgramFile(file);
    }
}
