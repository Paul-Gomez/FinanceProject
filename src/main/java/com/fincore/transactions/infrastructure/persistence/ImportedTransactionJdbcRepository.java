package com.fincore.transactions.infrastructure.persistence;

import com.fincore.transactions.application.ImportedTransaction;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Array;
import java.sql.Date;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Inserción en bloque para importaciones: un único INSERT de varias filas por lote, en vez de una
 * sentencia por movimiento. ON CONFLICT DO NOTHING sobre el índice único de huellas descarta los
 * duplicados en la propia base de datos (también los que mete otra importación a la vez), y
 * RETURNING dice cuáles se han insertado de verdad.
 */
@Repository
public class ImportedTransactionJdbcRepository {

    private static final int COLUMNS = 10;

    private final JdbcTemplate jdbc;

    public ImportedTransactionJdbcRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Devuelve huella -> id de los movimientos que se han insertado (los duplicados no aparecen). */
    public Map<String, UUID> insertIgnoringDuplicates(UUID accountId, String currencyCode, UUID importJobId,
                                                      List<ImportedTransaction> batch) {
        if (batch.isEmpty()) {
            return Map.of();
        }
        StringBuilder sql = new StringBuilder("""
                INSERT INTO transactions (account_id, type, status, amount, currency_code, category_id, description,
                                          transaction_date, reference, import_fingerprint, metadata, created_at)
                VALUES\s""");
        List<Object> args = new ArrayList<>(batch.size() * COLUMNS);
        for (int i = 0; i < batch.size(); i++) {
            ImportedTransaction tx = batch.get(i);
            sql.append(i == 0 ? "" : ", ")
                    .append("(?, ?, 'POSTED', ?, ?, ?, ?, ?, ?, ?, jsonb_build_object('importJobId', ?::text), now())");
            args.add(accountId);
            args.add(tx.type().name());
            args.add(tx.signedAmount());
            args.add(currencyCode);
            args.add(tx.categoryId());
            args.add(tx.description());
            args.add(Date.valueOf(tx.date()));
            args.add(tx.reference());
            args.add(tx.fingerprint());
            args.add(importJobId.toString());
        }
        sql.append("""
                 ON CONFLICT (account_id, import_fingerprint) WHERE import_fingerprint IS NOT NULL DO NOTHING
                 RETURNING id, import_fingerprint""");

        Map<String, UUID> inserted = new HashMap<>();
        jdbc.query(sql.toString(), rs -> {
            inserted.put(rs.getString("import_fingerprint"), rs.getObject("id", UUID.class));
        }, args.toArray());
        return inserted;
    }

    /** Qué huellas de la lista ya existen en la cuenta (para marcar duplicados antes de insertar). */
    public Set<String> existingFingerprints(UUID accountId, Collection<String> fingerprints) {
        if (fingerprints.isEmpty()) {
            return Set.of();
        }
        Set<String> existing = new HashSet<>();
        jdbc.query(connection -> {
            Array array = connection.createArrayOf("varchar", fingerprints.toArray());
            var statement = connection.prepareStatement(
                    "SELECT import_fingerprint FROM transactions WHERE account_id = ? AND import_fingerprint = ANY (?)");
            statement.setObject(1, accountId);
            statement.setArray(2, array);
            return statement;
        }, rs -> {
            existing.add(rs.getString(1));
        });
        return existing;
    }
}
