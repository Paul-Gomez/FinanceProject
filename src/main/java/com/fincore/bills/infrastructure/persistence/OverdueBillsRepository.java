package com.fincore.bills.infrastructure.persistence;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

@Repository
public class OverdueBillsRepository {

    public record OverdueBill(UUID id, UUID ownerId, String provider, LocalDate dueDate) {
    }

    private final NamedParameterJdbcTemplate jdbc;

    public OverdueBillsRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Pasa a OVERDUE todas las pendientes vencidas en una sola sentencia y devuelve cuáles han
     * cambiado (RETURNING). Es idempotente: si el job se ejecuta dos veces, la segunda no toca nada.
     * Las fechas van en UTC como LocalDateTime, igual que las guarda Hibernate (jdbc.time_zone=UTC).
     */
    public List<OverdueBill> markOverdue(LocalDate today, Instant now) {
        String sql = """
                UPDATE bills SET status = 'OVERDUE', updated_at = :now
                WHERE status = 'PENDING' AND due_date < :today
                RETURNING id, owner_id, provider, due_date
                """;
        return jdbc.query(sql, new MapSqlParameterSource().addValue("today", today).addValue("now", LocalDateTime.ofInstant(now, ZoneOffset.UTC)),
                (rs, i) -> new OverdueBill(rs.getObject("id", UUID.class), rs.getObject("owner_id", UUID.class),
                        rs.getString("provider"), rs.getObject("due_date", LocalDate.class)));
    }
}
