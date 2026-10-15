package com.fincore.imports.infrastructure.persistence;

import com.fincore.imports.domain.ImportRowResult;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Date;
import java.sql.Types;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public class ImportRowRepository {

    private final JdbcTemplate jdbc;

    public ImportRowRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** batchUpdate: un viaje a la base de datos por lote, no uno por fila. */
    public void saveAll(UUID importJobId, List<ImportRowResult> rows) {
        jdbc.batchUpdate("""
                INSERT INTO import_rows (import_job_id, row_number, status, transaction_date, amount, description,
                                         message, transaction_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """, rows, 500, (ps, row) -> {
            ps.setObject(1, importJobId);
            ps.setLong(2, row.rowNumber());
            ps.setString(3, row.status().name());
            if (row.date() != null) {
                ps.setDate(4, Date.valueOf(row.date()));
            } else {
                ps.setNull(4, Types.DATE);
            }
            ps.setBigDecimal(5, row.amount());
            ps.setString(6, truncate(row.description(), 255));
            ps.setString(7, truncate(row.message(), 500));
            ps.setObject(8, row.transactionId());
        });
    }

    public List<ImportRowResult> findByJob(UUID importJobId, ImportRowResult.Status status) {
        String sql = """
                SELECT row_number, status, transaction_date, amount, description, message, transaction_id
                FROM import_rows WHERE import_job_id = ? AND (?::varchar IS NULL OR status = ?::varchar)
                ORDER BY row_number
                """;
        String statusName = status != null ? status.name() : null;
        return jdbc.query(sql, (rs, i) -> new ImportRowResult(
                rs.getLong("row_number"),
                ImportRowResult.Status.valueOf(rs.getString("status")),
                rs.getObject("transaction_date", LocalDate.class),
                rs.getBigDecimal("amount"),
                rs.getString("description"),
                rs.getString("message"),
                rs.getObject("transaction_id", UUID.class)), importJobId, statusName, statusName);
    }

    private static String truncate(String value, int max) {
        return value != null && value.length() > max ? value.substring(0, max) : value;
    }
}
