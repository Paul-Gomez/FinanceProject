package com.fincore.shared.idempotency;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface IdempotencyRepository extends JpaRepository<IdempotencyRecord, UUID> {

    /**
     * Devuelve 1 si la clave era nueva y 0 si ya existía. Si otra transacción acaba de insertar la
     * misma clave y aún no ha confirmado, Postgres espera a que confirme o aborte antes de responder.
     */
    @Modifying
    @Query(value = """
            INSERT INTO idempotency_keys (id, user_id, idempotency_key, request_hash, created_at, expires_at)
            VALUES (gen_random_uuid(), :userId, :key, :hash, now(), :expiresAt)
            ON CONFLICT (user_id, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("userId") UUID userId, @Param("key") String key,
                       @Param("hash") String hash, @Param("expiresAt") Instant expiresAt);

    @Modifying
    @Query(value = """
            UPDATE idempotency_keys SET response_status = :status, response_body = :body
            WHERE user_id = :userId AND idempotency_key = :key
            """, nativeQuery = true)
    int storeResponse(@Param("userId") UUID userId, @Param("key") String key,
                      @Param("status") int status, @Param("body") String body);

    @Modifying
    @Query(value = """
            DELETE FROM idempotency_keys
            WHERE user_id = :userId AND idempotency_key = :key AND expires_at < :now
            """, nativeQuery = true)
    int deleteIfExpired(@Param("userId") UUID userId, @Param("key") String key, @Param("now") Instant now);

    @Modifying
    @Query(value = "DELETE FROM idempotency_keys WHERE expires_at < :now", nativeQuery = true)
    int deleteAllExpired(@Param("now") Instant now);

    Optional<IdempotencyRecord> findByUserIdAndKey(UUID userId, String key);
}
