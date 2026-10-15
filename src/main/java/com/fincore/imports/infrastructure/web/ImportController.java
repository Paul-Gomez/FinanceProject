package com.fincore.imports.infrastructure.web;

import com.fincore.identity.infrastructure.security.CurrentUserId;
import com.fincore.imports.application.ImportService;
import com.fincore.imports.application.InvalidImportFileException;
import com.fincore.imports.domain.ImportJob;
import com.fincore.imports.domain.ImportRowResult;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVPrinter;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/imports")
public class ImportController {

    private final ImportService importService;

    public ImportController(ImportService importService) {
        this.importService = importService;
    }

    public record ImportJobResponse(UUID id, UUID accountId, String fileName, ImportJob.Status status, int totalRows,
                                    int importedRows, int duplicateRows, int errorRows, String errorMessage,
                                    Instant createdAt, Instant finishedAt) {
        static ImportJobResponse from(ImportJob job) {
            return new ImportJobResponse(job.getId(), job.getAccountId(), job.getFileName(), job.getStatus(),
                    job.getTotalRows(), job.getImportedRows(), job.getDuplicateRows(), job.getErrorRows(),
                    job.getErrorMessage(), job.getCreatedAt(), job.getFinishedAt());
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportJobResponse> upload(@CurrentUserId UUID userId, @RequestParam UUID accountId,
                                                    @RequestParam("file") MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null || !(name.toLowerCase(Locale.ROOT).endsWith(".csv") || name.toLowerCase(Locale.ROOT).endsWith(".txt"))) {
            throw new InvalidImportFileException("solo se aceptan ficheros .csv");
        }
        try {
            ImportJob job = importService.importCsv(userId, accountId, name, file.getBytes());
            return ResponseEntity.status(HttpStatus.CREATED).body(ImportJobResponse.from(job));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @GetMapping
    public List<ImportJobResponse> list(@CurrentUserId UUID userId) {
        return importService.list(userId).stream().map(ImportJobResponse::from).toList();
    }

    @GetMapping("/{importId}")
    public ImportJobResponse get(@CurrentUserId UUID userId, @PathVariable UUID importId) {
        return ImportJobResponse.from(importService.get(importId, userId));
    }

    @GetMapping("/{importId}/rows")
    public List<ImportRowResult> rows(@CurrentUserId UUID userId, @PathVariable UUID importId,
                                      @RequestParam(required = false) ImportRowResult.Status status) {
        return importService.rows(importId, userId, status);
    }

    /** Informe descargable fila a fila. Con BOM y ';' para que Excel lo abra bien en español. */
    @GetMapping(value = "/{importId}/report", produces = "text/csv")
    public ResponseEntity<byte[]> report(@CurrentUserId UUID userId, @PathVariable UUID importId) {
        StringWriter out = new StringWriter();
        out.write('﻿');
        try (CSVPrinter printer = new CSVPrinter(out, CSVFormat.DEFAULT.builder().setDelimiter(';').build())) {
            printer.printRecord("fila", "estado", "fecha", "importe", "descripcion", "mensaje");
            for (ImportRowResult row : importService.rows(importId, userId, null)) {
                printer.printRecord(row.rowNumber(), row.status(), row.date(),
                        row.amount() != null ? row.amount().stripTrailingZeros().toPlainString() : null,
                        row.description(), row.message());
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("importacion-" + importId + ".csv").build().toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(out.toString().getBytes(StandardCharsets.UTF_8));
    }
}
