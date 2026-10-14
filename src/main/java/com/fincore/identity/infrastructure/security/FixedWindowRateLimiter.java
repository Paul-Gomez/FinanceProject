package com.fincore.identity.infrastructure.security;

import java.time.Clock;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Límite de peticiones por clave (la IP) en ventanas fijas de un minuto. Vive en memoria, así que
 * con varias instancias de la aplicación cada una contaría por su lado: en ese caso habría que
 * moverlo a Redis (por ejemplo con Bucket4j). Para una sola instancia es suficiente y no añade
 * ninguna dependencia.
 */
public class FixedWindowRateLimiter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int CLEANUP_THRESHOLD = 10_000;

    private final int limitPerWindow;
    private final Clock clock;
    private final ConcurrentMap<String, Window> windows = new ConcurrentHashMap<>();

    private record Window(long index, int count) {
    }

    public record Decision(boolean allowed, long retryAfterSeconds) {
    }

    public FixedWindowRateLimiter(int limitPerWindow, Clock clock) {
        this.limitPerWindow = limitPerWindow;
        this.clock = clock;
    }

    public Decision tryAcquire(String key) {
        long nowMillis = clock.millis();
        long currentIndex = nowMillis / WINDOW.toMillis();

        Window window = windows.compute(key, (k, existing) ->
                existing == null || existing.index() != currentIndex
                        ? new Window(currentIndex, 1)
                        : new Window(currentIndex, existing.count() + 1));

        if (windows.size() > CLEANUP_THRESHOLD) {
            windows.values().removeIf(w -> w.index() < currentIndex);
        }

        if (window.count() <= limitPerWindow) {
            return new Decision(true, 0);
        }
        long windowEndMillis = (currentIndex + 1) * WINDOW.toMillis();
        return new Decision(false, Math.max(1, (windowEndMillis - nowMillis) / 1000));
    }
}
