package com.fincore.shared.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * Ejecuta una operación como mucho una vez por (usuario, Idempotency-Key). La clave se reserva
 * con un INSERT dentro de la misma transacción que la operación: si dos peticiones llegan a la
 * vez, la segunda espera en el índice único hasta que la primera confirme y entonces recibe la
 * respuesta guardada. Si la operación falla, la transacción se deshace y la clave queda libre.
 */
@Service
public class IdempotencyService {

    private static final int MAX_KEY_LENGTH = 100;
    private static final Duration RETENTION = Duration.ofHours(24);

    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final ObjectMapper hashMapper;
    private final Clock clock;

    public IdempotencyService(IdempotencyRepository repository, ObjectMapper objectMapper, Clock clock) {
        this.repository = repository;
        this.objectMapper = objectMapper;
        this.clock = clock;
        // Orden fijo en los mapas para que el hash no cambie si el cliente reordena las claves de metadata.
        this.hashMapper = objectMapper.copy().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    public record Outcome<T>(T body, int status, boolean replayed) {
    }

    @Transactional
    public <T> Outcome<T> execute(UUID userId, String key, String endpoint, Object requestPayload,
                                  int successStatus, Class<T> responseType, Supplier<T> operation) {
        if (key == null) {
            return new Outcome<>(operation.get(), successStatus, false);
        }
        if (key.isBlank() || key.length() > MAX_KEY_LENGTH) {
            throw new InvalidIdempotencyKeyException();
        }

        String hash = hash(endpoint, requestPayload);
        Instant now = clock.instant();
        // Una clave caducada se trata como si no existiera, aunque el job de limpieza aún no la haya borrado.
        repository.deleteIfExpired(userId, key, now);
        int inserted = repository.insertIfAbsent(userId, key, hash, now.plus(RETENTION));

        if (inserted == 0) {
            IdempotencyRecord existing = repository.findByUserIdAndKey(userId, key).orElseThrow();
            if (!existing.getRequestHash().equals(hash)) {
                throw new IdempotencyKeyReusedException(key);
            }
            return new Outcome<>(fromJson(existing.getResponseBody(), responseType), existing.getResponseStatus(), true);
        }

        T body = operation.get();
        repository.storeResponse(userId, key, successStatus, toJson(body));
        return new Outcome<>(body, successStatus, false);
    }

    private String hash(String endpoint, Object payload) {
        try {
            byte[] input = (endpoint + "\n" + hashMapper.writeValueAsString(payload)).getBytes(StandardCharsets.UTF_8);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(input));
        } catch (JsonProcessingException | NoSuchAlgorithmException e) {
            throw new IllegalStateException("No se pudo calcular el hash de la petición", e);
        }
    }

    private String toJson(Object body) {
        try {
            return objectMapper.writeValueAsString(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo guardar la respuesta idempotente", e);
        }
    }

    private <T> T fromJson(String json, Class<T> type) {
        try {
            return objectMapper.readValue(json, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No se pudo leer la respuesta idempotente guardada", e);
        }
    }
}
