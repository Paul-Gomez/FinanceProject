package com.fincore.imports.application.parsing;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.List;

/** Formatos de fecha habituales en extractos bancarios. Estricto: "31/02/2026" es un error, no el 3 de marzo. */
public final class DateParser {

    private static final List<DateTimeFormatter> FORMATS = List.of(
            "uuuu-MM-dd", "dd/MM/uuuu", "d/M/uuuu", "dd-MM-uuuu", "dd.MM.uuuu").stream()
            .map(pattern -> DateTimeFormatter.ofPattern(pattern).withResolverStyle(ResolverStyle.STRICT))
            .toList();

    private DateParser() {
    }

    public static LocalDate parse(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("la fecha está vacía");
        }
        String value = raw.trim();
        // Algunos bancos añaden la hora: "15/10/2026 10:32".
        int space = value.indexOf(' ');
        if (space > 0) {
            value = value.substring(0, space);
        }
        for (DateTimeFormatter format : FORMATS) {
            try {
                return LocalDate.parse(value, format);
            } catch (DateTimeParseException ignored) {
                // se prueba el siguiente formato
            }
        }
        throw new IllegalArgumentException("'" + raw + "' no es una fecha válida");
    }
}
