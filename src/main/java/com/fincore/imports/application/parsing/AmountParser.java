package com.fincore.imports.application.parsing;

import java.math.BigDecimal;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Lee importes tal como los exportan los bancos: "1.234,56", "1,234.56", "-12,5", "12,50 €",
 * "(12.50)" o "12.50-". Reglas para los separadores:
 * - Si aparecen los dos, el último es el decimal y el otro es de miles.
 * - Si solo aparece uno y se repite, es de miles ("1.234.567").
 * - Si solo aparece una vez seguido de exactamente 3 dígitos ("1.234", "8,555") es ambiguo: se usa la
 *   convención decimal del propio fichero (ver detectDecimalSeparator). En un extracto donde los
 *   importes son "-54,30", "8,555" es un decimal de 3 cifras (y la validación de precisión lo
 *   rechazará), no 8.555 €. Si el fichero no da pistas, se toma como separador de miles.
 * - En cualquier otro caso es el separador decimal.
 */
public final class AmountParser {

    private static final Pattern NOISE = Pattern.compile("[\\s\\u00A0€$£]|EUR|USD|GBP", Pattern.CASE_INSENSITIVE);
    private static final Pattern VALID = Pattern.compile("[0-9.,]+");

    private AmountParser() {
    }

    public static BigDecimal parse(String raw) {
        return parse(raw, null);
    }

    /**
     * decimalSeparator: la convención del fichero, si se conoce (ver detectDecimalSeparator). Solo se
     * usa en el caso ambiguo de un único separador seguido de 3 dígitos.
     */
    public static BigDecimal parse(String raw, Character decimalSeparator) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("el importe está vacío");
        }
        String value = NOISE.matcher(raw).replaceAll("");
        boolean negative = false;
        if (value.startsWith("(") && value.endsWith(")")) {
            negative = true;
            value = value.substring(1, value.length() - 1);
        }
        if (value.startsWith("-") || value.startsWith("+")) {
            negative = value.startsWith("-");
            value = value.substring(1);
        } else if (value.endsWith("-")) {
            negative = true;
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || !VALID.matcher(value).matches()) {
            throw new IllegalArgumentException("'" + raw + "' no es un importe válido");
        }

        BigDecimal amount = new BigDecimal(normalizeSeparators(value, raw, decimalSeparator));
        return negative ? amount.negate() : amount;
    }

    private static String normalizeSeparators(String value, String raw, Character decimalSeparator) {
        int lastComma = value.lastIndexOf(',');
        int lastDot = value.lastIndexOf('.');

        if (lastComma >= 0 && lastDot >= 0) {
            char decimal = lastComma > lastDot ? ',' : '.';
            char thousands = decimal == ',' ? '.' : ',';
            requireThousandsGrouping(value.substring(0, value.lastIndexOf(decimal)), thousands, raw);
            return requireDigits(value.replace(String.valueOf(thousands), "").replace(decimal, '.'), raw);
        }
        if (lastComma < 0 && lastDot < 0) {
            return value;
        }

        char separator = lastComma >= 0 ? ',' : '.';
        int occurrences = value.length() - value.replace(String.valueOf(separator), "").length();
        int digitsAfter = value.length() - value.lastIndexOf(separator) - 1;
        boolean ambiguous = occurrences == 1 && digitsAfter == 3;
        boolean thousands = occurrences > 1 || (ambiguous && !Character.valueOf(separator).equals(decimalSeparator));

        if (thousands) {
            requireThousandsGrouping(value, separator, raw);
            return requireDigits(value.replace(String.valueOf(separator), ""), raw);
        }
        return requireDigits(value.replace(separator, '.'), raw);
    }

    /**
     * Separador decimal del fichero según sus importes no ambiguos: los que llevan los dos separadores
     * ("1.234,56" -> ',') o uno solo con 1 o 2 decimales ("-54,30" -> ','). Gana el más votado; null
     * si el fichero no da ninguna pista.
     */
    public static Character detectDecimalSeparator(List<String> values) {
        int comma = 0;
        int dot = 0;
        for (String raw : values) {
            if (raw == null) {
                continue;
            }
            String value = NOISE.matcher(raw).replaceAll("");
            int lastComma = value.lastIndexOf(',');
            int lastDot = value.lastIndexOf('.');
            if (lastComma >= 0 && lastDot >= 0) {
                if (lastComma > lastDot) {
                    comma++;
                } else {
                    dot++;
                }
            } else if (lastComma >= 0 || lastDot >= 0) {
                char separator = lastComma >= 0 ? ',' : '.';
                int separatorAt = Math.max(lastComma, lastDot);
                int digitsAfter = countDigits(value.substring(separatorAt + 1));
                boolean single = value.indexOf(separator) == separatorAt;
                if (single && (digitsAfter == 1 || digitsAfter == 2)) {
                    if (separator == ',') {
                        comma++;
                    } else {
                        dot++;
                    }
                }
            }
        }
        if (comma == dot) {
            return null;
        }
        return comma > dot ? ',' : '.';
    }

    private static int countDigits(String text) {
        return (int) text.chars().filter(Character::isDigit).count();
    }

    /** Los separadores de miles agrupan de tres en tres: "1.234.567" sí, "1.2.3" o "12.34" no. */
    private static void requireThousandsGrouping(String integerPart, char separator, String raw) {
        String[] groups = integerPart.split(Pattern.quote(String.valueOf(separator)), -1);
        boolean valid = !groups[0].isEmpty() && groups[0].length() <= 3;
        for (int i = 1; i < groups.length && valid; i++) {
            valid = groups[i].length() == 3;
        }
        if (!valid) {
            throw new IllegalArgumentException("'" + raw + "' no es un importe válido");
        }
    }

    private static String requireDigits(String normalized, String raw) {
        if (normalized.isEmpty() || normalized.startsWith(".") || normalized.endsWith(".")
                || normalized.indexOf('.') != normalized.lastIndexOf('.')) {
            throw new IllegalArgumentException("'" + raw + "' no es un importe válido");
        }
        return normalized;
    }
}
