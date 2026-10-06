package com.fincore.shared.money;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Currency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MoneyTest {

    private static final Currency EUR = Currency.getInstance("EUR");
    private static final Currency USD = Currency.getInstance("USD");

    @Test
    void addsAmountsInSameCurrency() {
        Money a = Money.of("10.50", "EUR");
        Money b = Money.of("2.25", "EUR");

        assertThat(a.add(b)).isEqualTo(Money.of("12.75", "EUR"));
    }

    @Test
    void subtractsAmountsInSameCurrency() {
        Money a = Money.of("10.00", "EUR");
        Money b = Money.of("4.30", "EUR");

        assertThat(a.subtract(b)).isEqualTo(Money.of("5.70", "EUR"));
    }

    @Test
    void rejectsOperationsAcrossDifferentCurrencies() {
        Money eur = Money.of("10.00", "EUR");
        Money usd = Money.of("10.00", "USD");

        assertThatThrownBy(() -> eur.add(usd))
                .isInstanceOf(CurrencyMismatchException.class);
    }

    @Test
    void scalesAmountToCurrencyDefaultFractionDigits() {
        Money money = Money.of(new BigDecimal("10.005"), EUR);

        // HALF_EVEN rounding at 2 decimal places
        assertThat(money.amount()).isEqualByComparingTo("10.00");
    }

    @Test
    void equalityIgnoresTrailingZeroScaleDifferences() {
        Money a = Money.of(new BigDecimal("10.5"), EUR);
        Money b = Money.of(new BigDecimal("10.50"), EUR);

        assertThat(a).isEqualTo(b);
    }

    @Test
    void ofExactRejectsMoreDecimalsThanTheCurrencyAllows() {
        assertThatThrownBy(() -> Money.ofExact(new BigDecimal("10.555"), EUR))
                .isInstanceOf(InvalidAmountPrecisionException.class);
        assertThatThrownBy(() -> Money.ofExact(new BigDecimal("10.5"), Currency.getInstance("JPY")))
                .isInstanceOf(InvalidAmountPrecisionException.class);
    }

    @Test
    void ofExactAcceptsAmountsWithinPrecision() {
        assertThat(Money.ofExact(new BigDecimal("10.5"), EUR)).isEqualTo(Money.of("10.50", "EUR"));
        assertThat(Money.ofExact(new BigDecimal("10.500"), EUR)).isEqualTo(Money.of("10.50", "EUR"));
        assertThat(Money.ofExact(new BigDecimal("1500"), Currency.getInstance("JPY")).amount())
                .isEqualByComparingTo("1500");
    }

    @Test
    void currencyForRejectsUnknownCodes() {
        assertThat(Money.currencyFor("EUR")).isEqualTo(EUR);
        assertThatThrownBy(() -> Money.currencyFor("ZZZ"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void currencyForRejectsCodesWithoutDefinedDecimals() {
        assertThatThrownBy(() -> Money.currencyFor("XAU"))
                .isInstanceOf(InvalidCurrencyException.class);
        assertThatThrownBy(() -> Money.currencyFor("XDR"))
                .isInstanceOf(InvalidCurrencyException.class);
    }

    @Test
    void zeroIsNeitherPositiveNorNegative() {
        Money zero = Money.zero(USD);

        assertThat(zero.isZero()).isTrue();
        assertThat(zero.isPositive()).isFalse();
        assertThat(zero.isNegative()).isFalse();
    }

    @Test
    void negateFlipsSign() {
        Money money = Money.of("5.00", "EUR");

        assertThat(money.negate()).isEqualTo(Money.of("-5.00", "EUR"));
        assertThat(money.negate().isNegative()).isTrue();
    }
}
