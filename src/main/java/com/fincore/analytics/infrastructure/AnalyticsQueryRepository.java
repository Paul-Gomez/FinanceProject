package com.fincore.analytics.infrastructure;

import com.fincore.shared.time.DateRange;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

/**
 * Consultas de analítica: todo se agrega en Postgres (SUM ... FILTER, GROUP BY, generate_series)
 * y a Java solo llegan los totales, nunca la lista de movimientos.
 * Reglas comunes: solo movimientos POSTED del usuario, en una moneda, dentro del rango, y solo
 * ingresos y gastos (las transferencias entre cuentas propias y los ajustes no son ni una cosa ni otra).
 * Los reembolsos cuentan como gasto negativo: un gasto de 100 con un reembolso de 30 son 70 gastados.
 * El índice (account_id, transaction_date) y el de accounts(owner_id) sirven a todas.
 */
@Repository
public class AnalyticsQueryRepository {

    private static final String SCOPE = """
            FROM transactions t
            JOIN accounts a ON a.id = t.account_id
            WHERE a.owner_id = :owner
              AND t.status = 'POSTED'
              AND t.type IN ('INCOME', 'EXPENSE', 'REFUND')
              AND t.currency_code = :currency
              AND t.transaction_date BETWEEN :from AND :to
            """;

    public record Totals(BigDecimal income, BigDecimal expenses, long movements) {
    }

    public record MonthTotals(YearMonth month, BigDecimal income, BigDecimal expenses) {
    }

    public record CategoryTotal(UUID categoryId, String name, String color, UUID parentId, BigDecimal total,
                                long movements) {
    }

    public record TopExpense(UUID id, LocalDate date, String description, BigDecimal amount, UUID categoryId,
                             String accountName) {
    }

    public record MerchantTotal(String merchant, long movements, BigDecimal total) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public AnalyticsQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Totals totals(UUID owner, String currency, DateRange range) {
        String sql = """
                SELECT COALESCE(SUM(t.amount) FILTER (WHERE t.type = 'INCOME'), 0)   AS income,
                       COALESCE(-SUM(t.amount) FILTER (WHERE t.type IN ('EXPENSE', 'REFUND')), 0) AS expenses,
                       COUNT(*)                                                     AS movements
                """ + SCOPE;
        return jdbc.queryForObject(sql, params(owner, currency, range), (rs, i) ->
                new Totals(rs.getBigDecimal("income"), rs.getBigDecimal("expenses"), rs.getLong("movements")));
    }

    /** Un registro por mes del rango, también los meses sin movimientos (a cero). */
    public List<MonthTotals> monthly(UUID owner, String currency, DateRange range) {
        String sql = """
                WITH months AS (
                    SELECT generate_series(date_trunc('month', CAST(:from AS timestamp)),
                                           date_trunc('month', CAST(:to AS timestamp)),
                                           interval '1 month') AS month
                ),
                movements AS (
                    SELECT t.amount, t.type, t.transaction_date
                """ + SCOPE + """
                )
                SELECT m.month,
                       COALESCE(SUM(x.amount) FILTER (WHERE x.type = 'INCOME'), 0)   AS income,
                       COALESCE(-SUM(x.amount) FILTER (WHERE x.type IN ('EXPENSE', 'REFUND')), 0) AS expenses
                FROM months m
                LEFT JOIN movements x ON date_trunc('month', CAST(x.transaction_date AS timestamp)) = m.month
                GROUP BY m.month
                ORDER BY m.month
                """;
        return jdbc.query(sql, params(owner, currency, range), (rs, i) -> new MonthTotals(
                YearMonth.from(rs.getObject("month", LocalDateTime.class)),
                rs.getBigDecimal("income"), rs.getBigDecimal("expenses")));
    }

    /**
     * Total por categoría. Con rollUp, lo de las subcategorías se suma a su categoría padre
     * (útil para ver "Alimentación" entera). Los movimientos sin categoría salen con categoryId null.
     */
    public List<CategoryTotal> byCategory(UUID owner, String currency, DateRange range, String type, boolean rollUp) {
        boolean expenses = "EXPENSE".equals(type);
        // Fragmentos fijos elegidos aquí (nunca texto del cliente): gastos netos de reembolsos, o ingresos.
        String types = expenses ? "'EXPENSE', 'REFUND'" : "'INCOME'";
        String amount = expenses ? "-t.amount" : "t.amount";
        String sql = """
                SELECT g.category_id, c.name, c.color, c.parent_id, g.total, g.movements
                FROM (
                    SELECT CASE WHEN :rollUp THEN COALESCE(cat.parent_id, t.category_id) ELSE t.category_id END
                               AS category_id,
                           SUM(%s) AS total,
                           COUNT(*) AS movements
                    FROM transactions t
                    JOIN accounts a ON a.id = t.account_id
                    LEFT JOIN categories cat ON cat.id = t.category_id
                    WHERE a.owner_id = :owner
                      AND t.status = 'POSTED'
                      AND t.type IN (%s)
                      AND t.currency_code = :currency
                      AND t.transaction_date BETWEEN :from AND :to
                    GROUP BY 1
                ) g
                LEFT JOIN categories c ON c.id = g.category_id
                ORDER BY g.total DESC
                """.formatted(amount, types);
        MapSqlParameterSource params = params(owner, currency, range).addValue("rollUp", rollUp);
        return jdbc.query(sql, params, (rs, i) -> new CategoryTotal(rs.getObject("category_id", UUID.class),
                rs.getString("name"), rs.getString("color"), rs.getObject("parent_id", UUID.class),
                rs.getBigDecimal("total"), rs.getLong("movements")));
    }

    public List<TopExpense> topExpenses(UUID owner, String currency, DateRange range, int limit) {
        String sql = """
                SELECT t.id, t.transaction_date, t.description, -t.amount AS amount, t.category_id, a.name AS account_name
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                WHERE a.owner_id = :owner
                  AND t.status = 'POSTED'
                  AND t.type = 'EXPENSE'
                  AND t.currency_code = :currency
                  AND t.transaction_date BETWEEN :from AND :to
                ORDER BY t.amount ASC, t.transaction_date DESC
                LIMIT :limit
                """;
        return jdbc.query(sql, params(owner, currency, range).addValue("limit", limit), (rs, i) -> new TopExpense(
                rs.getObject("id", UUID.class), rs.getObject("transaction_date", LocalDate.class),
                rs.getString("description"), rs.getBigDecimal("amount"), rs.getObject("category_id", UUID.class),
                rs.getString("account_name")));
    }

    /** "Comercio" = descripción en mayúsculas y sin espacios sobrantes. */
    public List<MerchantTotal> topMerchants(UUID owner, String currency, DateRange range, int limit) {
        String sql = """
                SELECT UPPER(TRIM(t.description)) AS merchant, COUNT(*) AS movements, SUM(-t.amount) AS total
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                WHERE a.owner_id = :owner
                  AND t.status = 'POSTED'
                  AND t.type = 'EXPENSE'
                  AND t.currency_code = :currency
                  AND t.transaction_date BETWEEN :from AND :to
                  AND t.description IS NOT NULL AND TRIM(t.description) <> ''
                GROUP BY 1
                ORDER BY total DESC
                LIMIT :limit
                """;
        return jdbc.query(sql, params(owner, currency, range).addValue("limit", limit), (rs, i) ->
                new MerchantTotal(rs.getString("merchant"), rs.getLong("movements"), rs.getBigDecimal("total")));
    }

    private static MapSqlParameterSource params(UUID owner, String currency, DateRange range) {
        return new MapSqlParameterSource()
                .addValue("owner", owner)
                .addValue("currency", currency)
                .addValue("from", range.from())
                .addValue("to", range.to());
    }
}
