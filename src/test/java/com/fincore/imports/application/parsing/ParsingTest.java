package com.fincore.imports.application.parsing;

import com.fincore.imports.application.InvalidImportFileException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParsingTest {

    @Test
    void datesInCommonFormatsAndStrict() {
        assertThat(DateParser.parse("15/10/2026")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(DateParser.parse("2026-10-15")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(DateParser.parse("15-10-2026")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThat(DateParser.parse("5/3/2026")).isEqualTo(LocalDate.of(2026, 3, 5));
        assertThat(DateParser.parse("15/10/2026 10:32")).isEqualTo(LocalDate.of(2026, 10, 15));
        assertThatThrownBy(() -> DateParser.parse("31/02/2026")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> DateParser.parse("ayer")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void detectsSpanishColumnsPreferringTheOperationDate() {
        ColumnMapping mapping = ColumnMapping.detect(List.of("Fecha valor", "Fecha operación", "Concepto", "Importe (EUR)", "Saldo"));

        assertThat(mapping.date()).isEqualTo(1);
        assertThat(mapping.amount()).isEqualTo(3);
        assertThat(mapping.description()).isEqualTo(2);
        assertThat(mapping.reference()).isEqualTo(-1);
    }

    @Test
    void detectsDebitAndCreditColumns() {
        ColumnMapping mapping = ColumnMapping.detect(List.of("Date", "Description", "Paid out", "Paid in", "Reference"));

        assertThat(mapping.amount()).isEqualTo(-1);
        assertThat(mapping.debit()).isEqualTo(2);
        assertThat(mapping.credit()).isEqualTo(3);
        assertThat(mapping.reference()).isEqualTo(4);
    }

    @Test
    void rejectsFilesWithoutDateOrAmount() {
        assertThatThrownBy(() -> ColumnMapping.detect(List.of("Nombre", "Apellidos")))
                .isInstanceOf(InvalidImportFileException.class);
    }

    @Test
    void readsWindows1252AndDetectsTheDelimiter() {
        String text = "Fecha;Concepto;Importe\n01/10/2026;Nómina octubre;1.500,00\n";
        CsvFileReader.CsvContent csv = CsvFileReader.read(text.getBytes(Charset.forName("windows-1252")), 100);

        assertThat(csv.delimiter()).isEqualTo(';');
        assertThat(csv.charset().name()).isEqualTo("windows-1252");
        assertThat(csv.rows().get(0).values()).containsExactly("01/10/2026", "Nómina octubre", "1.500,00");
        assertThat(csv.rows().get(0).rowNumber()).isEqualTo(2);
    }

    @Test
    void readsUtf8WithBomAndQuotedFieldsContainingTheDelimiter() {
        String text = "﻿Date,Description,Amount\n2026-10-01,\"Cena, con amigos\",-45.50\n";
        CsvFileReader.CsvContent csv = CsvFileReader.read(text.getBytes(StandardCharsets.UTF_8), 100);

        assertThat(csv.headers()).containsExactly("Date", "Description", "Amount");
        assertThat(csv.rows().get(0).values()).containsExactly("2026-10-01", "Cena, con amigos", "-45.50");
    }

    @Test
    void enforcesTheRowLimit() {
        String text = "Fecha;Importe\n01/10/2026;1\n02/10/2026;2\n03/10/2026;3\n";

        assertThatThrownBy(() -> CsvFileReader.read(text.getBytes(StandardCharsets.UTF_8), 2))
                .isInstanceOf(InvalidImportFileException.class);
    }

    @Test
    void identicalMovementsOnTheSameDayGetDifferentFingerprints() {
        UUID account = UUID.randomUUID();
        LocalDate day = LocalDate.of(2026, 10, 1);

        String first = Fingerprints.withoutReference(account, day, new BigDecimal("-1.50"), "Cafe", 1);
        String second = Fingerprints.withoutReference(account, day, new BigDecimal("-1.5"), "CAFÉ", 2);
        String firstAgain = Fingerprints.withoutReference(account, day, new BigDecimal("-1.500"), " café ", 1);

        assertThat(first).isNotEqualTo(second);
        assertThat(first).isEqualTo(firstAgain);
    }
}
