package com.fincore.imports.application.parsing;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmountParserTest {

    @ParameterizedTest(name = "\"{0}\" -> {1}")
    @CsvSource(delimiter = '|', value = {
            "-12,50|-12.50",
            "12.50|12.50",
            "1.234,56|1234.56",
            "1,234.56|1234.56",
            "-1.234.567,89|-1234567.89",
            "1,234,567.89|1234567.89",
            "1.234|1234",
            "1,234|1234",
            "12,5|12.5",
            "0,99|0.99",
            "12,50 €|12.50",
            "€ 12.50|12.50",
            "+300|300",
            "(45.10)|-45.10",
            "45,10-|-45.10",
            "1 234,56|1234.56",
            "100 EUR|100"
    })
    void parsesBankFormats(String raw, String expected) {
        assertThat(AmountParser.parse(raw)).isEqualByComparingTo(new BigDecimal(expected));
    }

    @Test
    void detectsTheDecimalConventionOfTheFile() {
        assertThat(AmountParser.detectDecimalSeparator(List.of("-54,30", "1.500,00", "-8,555"))).isEqualTo(',');
        assertThat(AmountParser.detectDecimalSeparator(List.of("-54.30", "1,500.00"))).isEqualTo('.');
        assertThat(AmountParser.detectDecimalSeparator(List.of("1.234", "100"))).isNull();
    }

    @Test
    void ambiguousAmountsFollowTheFileConvention() {
        assertThat(AmountParser.parse("-8,555", ',')).isEqualByComparingTo("-8.555");
        assertThat(AmountParser.parse("-8,555", '.')).isEqualByComparingTo("-8555");
        assertThat(AmountParser.parse("1.234", ',')).isEqualByComparingTo("1234");
        assertThat(AmountParser.parse("1.234", null)).isEqualByComparingTo("1234");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "  ", "abc", "12,34,5.6.7", "--5", "1.2.3", ",", "."})
    void rejectsGarbage(String raw) {
        assertThatThrownBy(() -> AmountParser.parse(raw)).isInstanceOf(IllegalArgumentException.class);
    }
}
