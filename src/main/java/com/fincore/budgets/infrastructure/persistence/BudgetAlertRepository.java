package com.fincore.budgets.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.UUID;

@Repository
public class BudgetAlertRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public BudgetAlertRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * true solo para quien inserta la fila. Si dos gastos llegan a la vez y los dos ven el
     * presupuesto al 80 %, solo uno consigue insertar y solo ese manda la alerta.
     */
    public boolean registerIfFirst(UUID budgetId, LocalDate periodStart, int threshold) {
        String sql = """
                INSERT INTO budget_alerts (budget_id, period_start, threshold)
                VALUES (:budgetId, :periodStart, :threshold)
                ON CONFLICT DO NOTHING
                """;
        return jdbc.update(sql, new MapSqlParameterSource()
                .addValue("budgetId", budgetId)
                .addValue("periodStart", periodStart)
                .addValue("threshold", threshold)) == 1;
    }
}
