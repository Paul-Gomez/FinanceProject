package com.fincore.imports.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Cómo ha acabado cada fila del fichero (para el informe). */
public record ImportRowResult(long rowNumber, Status status, LocalDate date, BigDecimal amount, String description,
                              String message, UUID transactionId) {

    public enum Status { IMPORTED, DUPLICATE, ERROR }
}
