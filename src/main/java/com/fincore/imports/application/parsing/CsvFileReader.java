package com.fincore.imports.application.parsing;

import com.fincore.imports.application.InvalidImportFileException;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import java.io.IOException;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Convierte los bytes del fichero en cabecera + filas.
 * - Codificación: UTF-8 si es válido (quitando el BOM que pone Excel); si no, Windows-1252, que es
 *   lo que exportan muchos bancos españoles. Así "Nómina" no acaba como "N�mina".
 * - Separador: el que más aparece en la cabecera entre ';', ',' y tabulador.
 */
public final class CsvFileReader {

    public record CsvRow(long rowNumber, List<String> values) {
    }

    public record CsvContent(List<String> headers, List<CsvRow> rows, Charset charset, char delimiter) {
    }

    private static final Charset WINDOWS_1252 = Charset.forName("windows-1252");

    private CsvFileReader() {
    }

    public static CsvContent read(byte[] bytes, int maxRows) {
        String text = decode(bytes);
        Charset charset = isUtf8(bytes) ? StandardCharsets.UTF_8 : WINDOWS_1252;
        if (text.isBlank()) {
            throw new InvalidImportFileException("está vacío");
        }

        char delimiter = detectDelimiter(firstLine(text));
        CSVFormat format = CSVFormat.DEFAULT.builder()
                .setDelimiter(delimiter)
                .setIgnoreEmptyLines(true)
                .setIgnoreSurroundingSpaces(true)
                .setTrim(true)
                .build();

        try (CSVParser parser = CSVParser.parse(new StringReader(text), format)) {
            List<String> headers = null;
            List<CsvRow> rows = new ArrayList<>();
            for (CSVRecord record : parser) {
                if (headers == null) {
                    headers = record.toList();
                    continue;
                }
                if (rows.size() >= maxRows) {
                    throw new InvalidImportFileException("tiene más de " + maxRows + " filas; divídelo en varios ficheros");
                }
                // Número de fila tal como lo vería el usuario en una hoja de cálculo (la cabecera es la 1).
                rows.add(new CsvRow(record.getRecordNumber(), record.toList()));
            }
            if (headers == null || rows.isEmpty()) {
                throw new InvalidImportFileException("no tiene filas de datos");
            }
            return new CsvContent(headers, rows, charset, delimiter);
        } catch (IOException | IllegalStateException | UncheckedIOException e) {
            throw new InvalidImportFileException("no es un CSV válido (" + e.getMessage() + ")");
        }
    }

    private static String decode(byte[] bytes) {
        String text = isUtf8(bytes) ? new String(bytes, StandardCharsets.UTF_8) : new String(bytes, WINDOWS_1252);
        return text.startsWith("﻿") ? text.substring(1) : text;
    }

    private static boolean isUtf8(byte[] bytes) {
        try {
            StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }

    private static String firstLine(String text) {
        for (String line : text.split("\\R")) {
            if (!line.isBlank()) {
                return line;
            }
        }
        return "";
    }

    static char detectDelimiter(String headerLine) {
        char best = ';';
        long bestCount = 0;
        for (char candidate : new char[]{';', ',', '\t'}) {
            long count = headerLine.chars().filter(c -> c == candidate).count();
            if (count > bestCount) {
                best = candidate;
                bestCount = count;
            }
        }
        return best;
    }
}
