/*
 * SPDX-FileCopyrightText: 2026 Fredrik Rambris <fredrik@rambris.com>
 * SPDX-License-Identifier: Apache-2.0
 * See LICENSE in the project root.
 */

package dev.rambris.amigaamos.bank;

import dev.rambris.amigaamos.dto.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static dev.rambris.amigaamos.JsonConfig.JSON;

/**
 * Programmatic API for reading, writing, importing, and exporting AMOS bank files.
 *
 * <p>This service encapsulates the full bank round-trip pipeline so that it can be
 * used both from the CLI ({@code portamos disasm / asm}) and from application code
 * without any dependency on the command-line layer.
 *
 * <p>Typical usage:
 * <pre>{@code
 * var svc = new AmosBankService();
 *
 * // Binary Abk → JSON + data files
 * AmosBank bank = svc.readBank(Path.of("Sprites.Abk"));
 * svc.exportBank(bank, Path.of("sprites-out/"), "Sprites");
 *
 * // JSON + data files → binary Abk
 * AmosBank bank = svc.importBank(Path.of("sprites-out/sprites.json"));
 * svc.writeBank(bank, Path.of("Sprites.Abk"));
 * }</pre>
 */
public class AmosBankService {

    // -------------------------------------------------------------------------
    // Binary I/O
    // -------------------------------------------------------------------------

    /**
     * Reads a bank from a binary {@code .Abk} file (AmBk, AmSp, AmIc, …).
     *
     * @param abkPath path to the bank file
     * @return the in-memory bank model
     * @throws IOException if the file cannot be read or the format is unrecognised
     */
    public AmosBank readBank(Path abkPath) throws IOException {
        return AmosBank.read(abkPath);
    }

    /**
     * Writes a bank to a binary {@code .Abk} file.
     *
     * @param bank    the bank to write
     * @param abkPath destination path
     * @throws IOException if the file cannot be written
     */
    public void writeBank(AmosBank bank, Path abkPath) throws IOException {
        bank.writer().write(bank, abkPath);
    }

    // -------------------------------------------------------------------------
    // Import (JSON + data files → AmosBank)
    // -------------------------------------------------------------------------

    /**
     * Imports a bank from a JSON metadata file (and its associated data files).
     *
     * <p>The bank type is auto-detected from the {@code "type"} field in the JSON using
     * {@link AmosBankDto} polymorphic deserialization, then delegated to the appropriate
     * {@code *BankImporter}.
     *
     * @param jsonPath path to the {@code bank.json} (or equivalent) metadata file
     * @return the reconstructed in-memory bank
     * @throws IOException              if any file cannot be read
     * @throws IllegalArgumentException if the {@code "type"} field is missing or unknown
     */
    public AmosBank importBank(Path jsonPath) throws IOException {
        var dto = JSON.readValue(jsonPath.toFile(), AmosBankDto.class);
        return switch (dto) {
            case AmalBankDto ignored -> new AmalBankImporter().importFrom(jsonPath);
            case MenuBankDto ignored -> new MenuBankImporter().importFrom(jsonPath);
            case MusicBankDto ignored -> new MusicBankImporter().importFrom(jsonPath);
            case PacPicBankDto ignored -> new PacPicBankImporter().importFrom(jsonPath);
            case RawBankDto ignored -> new RawBankImporter().importFrom(jsonPath);
            case ResourceBankDto ignored -> new ResourceBankImporter().importFrom(jsonPath);
            case SampleBankDto ignored -> new SampleBankImporter().importFrom(jsonPath);
            case SpriteBankDto ignored -> new SpriteBankImporter().importFrom(jsonPath);
            case TrackerBankDto ignored -> new TrackerBankImporter().importFrom(jsonPath);
            default -> throw new IllegalArgumentException("Unknown bank type: " + dto.type());
        };
    }

    // -------------------------------------------------------------------------
    // Export (AmosBank → JSON + data files)
    // -------------------------------------------------------------------------

    /**
     * Exports a bank to {@code outDir} using default options (PNG images, WAVE audio).
     *
     * @param bank   the bank to export
     * @param outDir destination directory (created if absent)
     * @param stem   filename stem used for banks that produce a single output file
     *               (PacPic writes {@code stem.png}; Raw writes {@code stem.bin})
     * @throws IOException if any file cannot be written
     */
    public void exportBank(AmosBank bank, Path outDir, String stem) throws IOException {
        exportBank(bank, outDir, stem, false, false);
    }

    /**
     * Exports a bank to {@code outDir}.
     *
     * @param bank   the bank to export
     * @param outDir destination directory (created if absent)
     * @param stem   filename stem used for banks that produce a single output file
     *               (PacPic writes {@code stem.png} or {@code stem.iff};
     *               Raw writes {@code stem.bin})
     * @param ilbm   export sprite/icon spritesheets and PacPic images as IFF ILBM
     *               instead of PNG
     * @param svx8   export instrument/sample audio as IFF 8SVX instead of RIFF WAVE
     * @throws IOException if any file cannot be written
     */
    public void exportBank(AmosBank bank, Path outDir, String stem, boolean ilbm, boolean svx8)
            throws IOException {
        Files.createDirectories(outDir);
        switch (bank) {
            case SpriteBank sb -> new SpriteBankExporter().export(sb, outDir, ilbm);
            case ResourceBank rb -> new ResourceBankExporter().export(rb, outDir, ilbm);
            case AmalBank ab -> new AmalBankExporter().export(ab, outDir);
            case MenuBank mb -> new MenuBankExporter().export(mb, outDir);
            case MusicBank mb -> new MusicBankExporter().export(mb, outDir, svx8);
            case SampleBank sb -> new SampleBankExporter().export(sb, outDir, svx8);
            case TrackerBank tb -> new TrackerBankExporter().export(tb, outDir);
            case PacPicBank pb -> {
                var ext = ilbm ? ".iff" : ".png";
                new PacPicBankExporter().export(pb, outDir.resolve(stem + ext), ilbm);
            }
            case RawBank rb -> new RawBankExporter().export(rb, outDir.resolve(stem + ".bin"));
            default -> throw new IllegalArgumentException("Unsupported bank type: " + bank.type());
        }
    }
}
