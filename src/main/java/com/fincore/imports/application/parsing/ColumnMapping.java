package com.fincore.imports.application.parsing;

import com.fincore.imports.application.InvalidImportFileException;
import com.fincore.shared.text.TextNormalizer;

import java.util.List;

/**
 * Qué columna del fichero es cada dato, detectado por el nombre de la cabecera (en español o
 * inglés). Los alias van en orden de preferencia: si hay "Fecha operación" y "Fecha valor", se
 * usa la de operación, que es cuando realmente se hizo el gasto.
 */
public record ColumnMapping(int date, int amount, int debit, int credit, int description, int reference) {

    private static final List<String> DATE = List.of("FECHA OPERACION", "F. OPERACION", "FECHA", "DATE",
            "TRANSACTION DATE", "BOOKING DATE", "FECHA CONTABLE", "FECHA VALOR", "VALUE DATE");
    private static final List<String> AMOUNT = List.of("IMPORTE", "CANTIDAD", "AMOUNT", "VALOR");
    private static final List<String> DEBIT = List.of("CARGO", "CARGOS", "DEBE", "DEBIT", "WITHDRAWAL", "PAID OUT");
    private static final List<String> CREDIT = List.of("ABONO", "ABONOS", "HABER", "CREDIT", "DEPOSIT", "PAID IN");
    private static final List<String> DESCRIPTION = List.of("CONCEPTO", "DESCRIPCION", "DETALLE", "MOVIMIENTO",
            "DESCRIPTION", "CONCEPT", "NARRATIVE", "DETAILS");
    private static final List<String> REFERENCE = List.of("REFERENCIA", "REF", "REFERENCE", "ID OPERACION",
            "TRANSACTION ID", "ID");

    public static ColumnMapping detect(List<String> headers) {
        List<String> normalized = headers.stream().map(TextNormalizer::normalize).toList();

        int date = find(normalized, DATE);
        int amount = find(normalized, AMOUNT);
        int debit = find(normalized, DEBIT);
        int credit = find(normalized, CREDIT);
        if (date < 0 || (amount < 0 && debit < 0 && credit < 0)) {
            throw new InvalidImportFileException("no se encuentran las columnas de fecha e importe. Cabeceras del fichero: "
                    + headers);
        }
        return new ColumnMapping(date, amount, debit, credit, find(normalized, DESCRIPTION), find(normalized, REFERENCE));
    }

    /** Primero coincidencia exacta y, si no hay, cabecera que empieza por el alias ("IMPORTE (EUR)"). */
    private static int find(List<String> headers, List<String> aliases) {
        for (String alias : aliases) {
            int exact = headers.indexOf(alias);
            if (exact >= 0) {
                return exact;
            }
        }
        for (String alias : aliases) {
            for (int i = 0; i < headers.size(); i++) {
                if (headers.get(i).startsWith(alias + " ") || headers.get(i).startsWith(alias + "(")) {
                    return i;
                }
            }
        }
        return -1;
    }
}
