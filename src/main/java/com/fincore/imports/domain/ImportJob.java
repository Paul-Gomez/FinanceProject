package com.fincore.imports.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "import_jobs")
public class ImportJob {

    public enum Status { PROCESSING, COMPLETED, FAILED }

    @Id
    @GeneratedValue
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    @Column(name = "file_name", updatable = false)
    private String fileName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;

    @Column(name = "total_rows", nullable = false)
    private int totalRows;

    @Column(name = "imported_rows", nullable = false)
    private int importedRows;

    @Column(name = "duplicate_rows", nullable = false)
    private int duplicateRows;

    @Column(name = "error_rows", nullable = false)
    private int errorRows;

    @Column(name = "error_message")
    private String errorMessage;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    protected ImportJob() {
    }

    public ImportJob(UUID ownerId, UUID accountId, String fileName, Instant now) {
        this.ownerId = ownerId;
        this.accountId = accountId;
        this.fileName = fileName != null && fileName.length() > 255 ? fileName.substring(0, 255) : fileName;
        this.status = Status.PROCESSING;
        this.createdAt = now;
    }

    public void complete(int total, int imported, int duplicates, int errors, Instant now) {
        this.totalRows = total;
        this.importedRows = imported;
        this.duplicateRows = duplicates;
        this.errorRows = errors;
        this.status = Status.COMPLETED;
        this.finishedAt = now;
    }

    public void fail(String message, Instant now) {
        this.status = Status.FAILED;
        this.errorMessage = message != null && message.length() > 500 ? message.substring(0, 500) : message;
        this.finishedAt = now;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public UUID getAccountId() {
        return accountId;
    }

    public String getFileName() {
        return fileName;
    }

    public Status getStatus() {
        return status;
    }

    public int getTotalRows() {
        return totalRows;
    }

    public int getImportedRows() {
        return importedRows;
    }

    public int getDuplicateRows() {
        return duplicateRows;
    }

    public int getErrorRows() {
        return errorRows;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }
}
