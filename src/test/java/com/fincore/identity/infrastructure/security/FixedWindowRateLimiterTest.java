package com.fincore.identity.infrastructure.security;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class FixedWindowRateLimiterTest {

    private static final Instant START = Instant.parse("2026-10-14T10:00:10Z");

    private static final class MutableClock extends Clock {
        private Instant now = START;

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @Test
    void allowsUpToTheLimitAndThenBlocksWithRetryAfter() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(3, new MutableClock());

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();

        FixedWindowRateLimiter.Decision blocked = limiter.tryAcquire("1.1.1.1");
        assertThat(blocked.allowed()).isFalse();
        assertThat(blocked.retryAfterSeconds()).isEqualTo(50);
    }

    @Test
    void eachKeyHasItsOwnCounter() {
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, new MutableClock());

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
        assertThat(limiter.tryAcquire("2.2.2.2").allowed()).isTrue();
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();
    }

    @Test
    void counterResetsInTheNextWindow() {
        MutableClock clock = new MutableClock();
        FixedWindowRateLimiter limiter = new FixedWindowRateLimiter(1, clock);
        limiter.tryAcquire("1.1.1.1");
        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(50));

        assertThat(limiter.tryAcquire("1.1.1.1").allowed()).isTrue();
    }
}
