package com.fincore.shared.text;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Pattern;

/** Normaliza textos de movimientos bancarios para compararlos: mayúsculas, sin tildes, espacios simples. */
public final class TextNormalizer {

    private static final Pattern DIACRITICS = Pattern.compile("\\p{M}+");
    private static final Pattern SPACES = Pattern.compile("\\s+");

    private TextNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String withoutAccents = DIACRITICS.matcher(Normalizer.normalize(text, Normalizer.Form.NFKD)).replaceAll("");
        return SPACES.matcher(withoutAccents.toUpperCase(Locale.ROOT).trim()).replaceAll(" ");
    }
}
