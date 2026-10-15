package com.fincore.exchangerates.application;

import com.fincore.shared.error.BusinessException;
import com.fincore.shared.error.ErrorType;
import com.fincore.shared.money.Money;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Currency;
import java.util.List;
import java.util.Optional;

/**
 * Tipos de cambio históricos y conversión entre monedas. Todavía no hay un proveedor automático:
 * los tipos los carga un administrador (o en el futuro un job que los descargue). La conversión
 * usa el último tipo publicado en la fecha indicada o antes, y si solo existe el par contrario
 * usa su inverso. Si no hay ningún tipo, falla de forma explícita en vez de inventarse uno.
 */
@Service
public class ExchangeRateService {

    public record ExchangeRate(String base, String quote, LocalDate date, BigDecimal rate, String source) {
    }

    public static class ExchangeRateNotAvailableException extends BusinessException {
        public ExchangeRateNotAvailableException(String from, String to, LocalDate date) {
            super(ErrorType.UNPROCESSABLE, "EXCHANGE_RATE_NOT_AVAILABLE",
                    "No hay tipo de cambio de %s a %s para el %s".formatted(from, to, date));
        }
    }

    public static class InvalidExchangeRateException extends BusinessException {
        public InvalidExchangeRateException() {
            super(ErrorType.UNPROCESSABLE, "INVALID_EXCHANGE_RATE",
                    "El tipo de cambio tiene que ser entre dos monedas distintas y mayor que cero");
        }
    }

    private static final MathContext PRECISION = new MathContext(20, RoundingMode.HALF_EVEN);

    private final NamedParameterJdbcTemplate jdbc;

    public ExchangeRateService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void save(String base, String quote, LocalDate date, BigDecimal rate, String source) {
        Currency from = Money.currencyFor(base);
        Currency to = Money.currencyFor(quote);
        if (from.equals(to) || rate.signum() <= 0) {
            throw new InvalidExchangeRateException();
        }
        jdbc.update("""
                INSERT INTO exchange_rates (base_currency, quote_currency, rate_date, rate, source)
                VALUES (:base, :quote, :date, :rate, :source)
                ON CONFLICT (base_currency, quote_currency, rate_date)
                DO UPDATE SET rate = EXCLUDED.rate, source = EXCLUDED.source
                """, new MapSqlParameterSource().addValue("base", from.getCurrencyCode())
                .addValue("quote", to.getCurrencyCode()).addValue("date", date).addValue("rate", rate)
                .addValue("source", source != null ? source : "manual"));
    }

    @Transactional(readOnly = true)
    public List<ExchangeRate> history(String base, String quote) {
        return jdbc.query("""
                SELECT base_currency, quote_currency, rate_date, rate, source FROM exchange_rates
                WHERE base_currency = :base AND quote_currency = :quote ORDER BY rate_date DESC LIMIT 365
                """, new MapSqlParameterSource().addValue("base", base).addValue("quote", quote),
                (rs, i) -> new ExchangeRate(rs.getString(1), rs.getString(2), rs.getObject(3, LocalDate.class),
                        rs.getBigDecimal(4), rs.getString(5)));
    }

    /**
     * Factor para pasar de una moneda a otra en una fecha (1 si son la misma), o vacío si no hay tipo.
     * Devuelve Optional en vez de lanzar: que falte un tipo es un caso normal, y una excepción que
     * atraviesa un método @Transactional marca la transacción de quien llama como "solo rollback"
     * aunque luego se capture, y el commit final falla con UnexpectedRollbackException.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> findRate(Currency from, Currency to, LocalDate date) {
        if (from.equals(to)) {
            return Optional.of(BigDecimal.ONE);
        }
        return latestRate(from.getCurrencyCode(), to.getCurrencyCode(), date)
                .or(() -> latestRate(to.getCurrencyCode(), from.getCurrencyCode(), date)
                        .map(inverse -> BigDecimal.ONE.divide(inverse, PRECISION)));
    }

    /** Convierte y redondea una sola vez, al final; vacío si no hay tipo de cambio. */
    @Transactional(readOnly = true)
    public Optional<Money> tryConvert(Money amount, Currency target, LocalDate date) {
        return findRate(amount.currency(), target, date)
                .map(rate -> Money.of(amount.amount().multiply(rate, PRECISION), target));
    }

    /** Como tryConvert, pero para quien necesita el importe sí o sí: sin tipo, error 422. */
    @Transactional(readOnly = true)
    public Money convert(Money amount, Currency target, LocalDate date) {
        return tryConvert(amount, target, date).orElseThrow(() -> new ExchangeRateNotAvailableException(
                amount.currency().getCurrencyCode(), target.getCurrencyCode(), date));
    }

    private Optional<BigDecimal> latestRate(String base, String quote, LocalDate date) {
        return jdbc.query("""
                SELECT rate FROM exchange_rates
                WHERE base_currency = :base AND quote_currency = :quote AND rate_date <= :date
                ORDER BY rate_date DESC LIMIT 1
                """, new MapSqlParameterSource().addValue("base", base).addValue("quote", quote).addValue("date", date),
                (rs, i) -> rs.getBigDecimal(1)).stream().findFirst();
    }
}
