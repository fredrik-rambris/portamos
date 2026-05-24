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
import java.nio.file.StandardOpenOption;

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
 * svc.exportBank(bank, Path.of("sprites-out/mysprites.json"));
 *
 * // JSON + data files → binary Abk
 * AmosBank bank = svc.importBank(Path.of("sprites-out/mysprites.json"));
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
     * Exports a bank to {@code jsonPath} using default options (PNG images, WAVE audio).
     *
     * @param bank     the bank to export
     * @param jsonPath destination JSON metadata file (data files written alongside it)
     * @throws IOException if any file cannot be written
     */
    public void exportBank(AmosBank bank, Path jsonPath) throws IOException {
        exportBank(bank, jsonPath, false, false, false);
    }

    /**
     * Exports a bank to {@code jsonPath}.
     *
     * <p>All associated data files are written as siblings of the JSON file, using
     * the JSON file's stem as a filename prefix.
     *
     * @param bank     the bank to export
     * @param jsonPath destination JSON metadata file (data files written alongside it)
     * @param ilbm     export sprite/icon spritesheets and PacPic images as IFF ILBM
     *                 instead of PNG
     * @param svx8     export instrument/sample audio as IFF 8SVX instead of RIFF WAVE
     * @throws IOException if any file cannot be written
     */
    public void exportBank(AmosBank bank, Path jsonPath, boolean ilbm, boolean svx8)
            throws IOException {
        exportBank(bank, jsonPath, ilbm, svx8, false);
    }

    /**
     * Exports a bank to {@code jsonPath}.
     *
     * @param bank     the bank to export
     * @param jsonPath destination JSON metadata file (data files written alongside it)
     * @param ilbm     export sprite/icon spritesheets and PacPic images as IFF ILBM
     *                 instead of PNG
     * @param svx8     export instrument/sample audio as IFF 8SVX instead of RIFF WAVE
     * @param useNames for sample banks: derive audio filenames from sample names
     *                 instead of zero-padded indices
     * @throws IOException if any file cannot be written
     */
    public void exportBank(AmosBank bank, Path jsonPath, boolean ilbm, boolean svx8,
                           boolean useNames) throws IOException {
        switch (bank) {
            case SpriteBank sb -> new SpriteBankExporter().export(sb, jsonPath, ilbm);
            case ResourceBank rb -> new ResourceBankExporter().export(rb, jsonPath, ilbm);
            case AmalBank ab -> new AmalBankExporter().export(ab, jsonPath);
            case MenuBank mb -> new MenuBankExporter().export(mb, jsonPath);
            case MusicBank mb -> new MusicBankExporter().export(mb, jsonPath, svx8);
            case SampleBank sb -> new SampleBankExporter().export(sb, jsonPath, svx8, useNames);
            case TrackerBank tb -> new TrackerBankExporter().export(tb, jsonPath);
            case PacPicBank pb -> new PacPicBankExporter().export(pb, jsonPath, ilbm);
            case RawBank rb -> new RawBankExporter().export(rb, jsonPath);
            default -> throw new IllegalArgumentException("Unsupported bank type: " + bank.type());
        }
    }

    /**
     * Returns the filename stem (everything before the last dot).
     */
    public static String stem(Path path) {
        var name = path.getFileName().toString();
        var dot = name.lastIndexOf('.');
        return dot > 0 ? name.substring(0, dot) : name;
    }

    public static Class identify(Path path) {
        var extension = AmosBankService.fileExtension(path).toLowerCase();
        try (var in = Files.newInputStream(path, StandardOpenOption.READ)) {
            var hdr = in.readNBytes(AmosBank.MIN_HEADER_SIZE);
            if (hdr[0] == '{' && extension.equals("json")) {
                return JSON.readValue(path.toFile(), AmosBankDto.class).getClass();
            } else return AmosBank.identify(hdr).bankClass();
        } catch (IOException e) {
            System.err.println("Failed to identify AMOS bank file: " + path + ": " + e.getMessage());
            return null;
        }
    }

    public static String fileExtension(Path path) {
        if (path != null) {
            var filename = path.getFileName().toString();
            var dotIndex = filename.lastIndexOf('.');
            if (dotIndex >= 0) {
                return filename.substring(dotIndex + 1);
            }
        }
        return "";
    }
}
