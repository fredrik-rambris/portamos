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


    public void export(AmalBank bank, Path jsonPath) throws IOException {
        var dir = jsonPath.toAbsolutePath().getParent();
        var stem = AmosBankService.stem(jsonPath);
        Files.createDirectories(dir);
        exportEnvironment(bank, dir, stem);
        exportPrograms(bank, dir, stem);
        exportMovements(bank, dir, stem);
        exportMetadata(bank, jsonPath, stem);
    }

    private void exportEnvironment(AmalBank bank, Path dir, String stem) throws IOException {
        if (bank.environment() == null || bank.environment().isEmpty()) return;
        var dest = dir.resolve(stem + "-environment.amal");
        Files.writeString(dest, bank.environment().replace("~", "\n"), StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // AMAL programs
    // -------------------------------------------------------------------------

    private void exportPrograms(AmalBank bank, Path dir, String stem) throws IOException {
        int exported = 0;
        for (int i = 0; i < bank.programs().size(); i++) {
            var program = bank.programs().get(i);
            if (program == null || program.isEmpty()) continue;
            var dest = dir.resolve(stem + "-program%03d.amal".formatted(i));
            // AMAL uses ~ as a line separator; convert to newlines for human-readable text
            Files.writeString(dest, program.replace("~", "\n"), StandardCharsets.UTF_8);
            exported++;
        }
        System.out.printf("Exported %d AMAL program(s) (of %d slots)%n",
                exported, bank.programs().size());
    }

    // -------------------------------------------------------------------------
    // Movement data
    // -------------------------------------------------------------------------

    private void exportMovements(AmalBank bank, Path dir, String stem) throws IOException {
        int exported = 0;
        for (int i = 0; i < bank.movements().size(); i++) {
            var mov = bank.movements().get(i);
            if (mov.isEmpty()) continue;
            var dest = dir.resolve(stem + "-movement%03d.json".formatted(i));
            JSON.writeValue(dest.toFile(), buildMovementDto(mov));
            exported++;
        }
        System.out.printf("Exported %d movement(s) (of %d slots)%n",
                exported, bank.movements().size());
    }

    private AmalMovementDto buildMovementDto(AmalBank.Movement mov) {
        return new AmalMovementDto(
                mov.name(),
                mov.xMove() != null ? buildMovementDataDto(mov.xMove()) : null,
                mov.yMove() != null ? buildMovementDataDto(mov.yMove()) : null);
    }

    private AmalMovementDto.MovementDataDto buildMovementDataDto(AmalBank.MovementData data) {
        var instructions = data.instructions().stream()
                .map(inst -> switch (inst) {
                    case AmalBank.Instruction.Wait wait -> new AmalMovementDto.InstructionDto("wait", wait.ticks(), null);
                    case AmalBank.Instruction.Delta delta -> new AmalMovementDto.InstructionDto("delta", null, delta.pixels());
                })
                .toList();
        return new AmalMovementDto.MovementDataDto(data.speed(), instructions);
    }

    // -------------------------------------------------------------------------
    // Metadata JSON
    // -------------------------------------------------------------------------

    private void exportMetadata(AmalBank bank, Path jsonPath, String stem) throws IOException {
        var movementRefs = new ArrayList<AmalBankDto.MovementRefDto>();
        for (int i = 0; i < bank.movements().size(); i++) {
            var mov = bank.movements().get(i);
            var file = mov.isEmpty() ? null : stem + "-movement%03d.json".formatted(i);
            movementRefs.add(new AmalBankDto.MovementRefDto(i, mov.name(), file));
        }

        var programRefs = new ArrayList<AmalBankDto.ProgramRefDto>();
        for (int i = 0; i < bank.programs().size(); i++) {
            var prog = bank.programs().get(i);
            if (prog == null || prog.isEmpty()) continue;
            programRefs.add(new AmalBankDto.ProgramRefDto(i, stem + "-program%03d.amal".formatted(i)));
        }

        var environment = (bank.environment() != null && !bank.environment().isEmpty())
                ? stem + "-environment.amal" : null;

        var dto = new AmalBankDto(
                AmalBankDto.TYPE,
                bank.bankNumber() & 0xFFFF,
                bank.chipRam(),
                environment,
                List.copyOf(movementRefs),
                bank.programs().size(),
                List.copyOf(programRefs));

        JSON.writeValue(jsonPath.toFile(), dto);
        System.out.printf("Written %s%n", jsonPath);
    }
}
