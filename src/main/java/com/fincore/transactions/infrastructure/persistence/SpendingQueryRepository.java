package com.fincore.transactions.infrastructure.persistence;

import com.fincore.shared.time.DateRange;
import com.fincore.transactions.application.CategorySpending;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Consultas de solo lectura que agregan en la base de datos: devuelven totales, no movimientos.
 * Se hacen con SQL directo porque son SUM/GROUP BY y no hace falta cargar ninguna entidad.
 */
@Repository
public class SpendingQueryRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public SpendingQueryRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<CategorySpending> expensesByCategory(UUID ownerId, DateRange range) {
        String sql = """
                SELECT t.category_id, t.currency_code, SUM(-t.amount) AS total
                FROM transactions t
                JOIN accounts a ON a.id = t.account_id
                WHERE a.owner_id = :ownerId
                  AND t.type IN ('EXPENSE', 'REFUND')
                  AND t.status = 'POSTED'
                  AND t.category_id IS NOT NULL
                  AND t.transaction_date BETWEEN :from AND :to
                GROUP BY t.category_id, t.currency_code
                """;
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("ownerId", ownerId)
                .addValue("from", range.from())
                .addValue("to", range.to());

        return jdbc.query(sql, params, (rs, i) -> new CategorySpending(
                rs.getObject("category_id", UUID.class), rs.getString("currency_code"), rs.getBigDecimal("total")));
    }
}
