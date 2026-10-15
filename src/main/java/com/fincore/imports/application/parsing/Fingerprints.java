package com.fincore.imports.application.parsing;

import com.fincore.shared.crypto.SecureTokens;
import com.fincore.shared.text.TextNormalizer;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Huella de un movimiento bancario para detectar duplicados.
 * - Con referencia del banco: cuenta + referencia (lo más fiable).
 * - Sin referencia: cuenta + fecha + importe + descripción normalizada + número de aparición dentro
 *   del fichero. Ese número evita el falso positivo clásico: dos cafés iguales el mismo día no son
 *   un duplicado, son el 1º y el 2º. Y como un mismo extracto produce siempre los mismos números,
 *   reimportarlo se sigue detectando.
 */
public final class Fingerprints {

    private Fingerprints() {
    }

    public static String withReference(UUID accountId, String reference) {
        return SecureTokens.sha256(accountId + "|ref|" + TextNormalizer.normalize(reference));
    }

    public static String withoutReference(UUID accountId, LocalDate date, BigDecimal amount, String description,
                                          int occurrence) {
        return SecureTokens.sha256(String.join("|", accountId.toString(), date.toString(),
                amount.stripTrailingZeros().toPlainString(), TextNormalizer.normalize(description),
                String.valueOf(occurrence)));
    }

    /** Clave de "mismo movimiento" dentro del fichero, para contar las apariciones. */
    public static String occurrenceKey(LocalDate date, BigDecimal amount, String description) {
        return date + "|" + amount.stripTrailingZeros().toPlainString() + "|" + TextNormalizer.normalize(description);
    }
}
