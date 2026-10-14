package com.fincore.shared.text;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void uppercasesRemovesAccentsAndCollapsesSpaces() {
        assertThat(TextNormalizer.normalize("  Compra   en Mercadona Pº Castellana  "))
                .isEqualTo("COMPRA EN MERCADONA PO CASTELLANA");
        assertThat(TextNormalizer.normalize("Nómina Octubre")).isEqualTo("NOMINA OCTUBRE");
        assertThat(TextNormalizer.normalize(null)).isEmpty();
    }
}
