package com.docmind.ratelimit;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class ApiKeyRateLimiterTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z"));
    private final ApiKeyRateLimiter limiter = new ApiKeyRateLimiter(
            new RateLimitProperties(true, 2, 1, Duration.ofSeconds(10)), clock);

    @Test
    void allowsUpToTheBurstThenRejects() {
        assertThat(limiter.tryConsume("key-a").allowed()).isTrue();
        assertThat(limiter.tryConsume("key-a").allowed()).isTrue();

        ApiKeyRateLimiter.Decision denied = limiter.tryConsume("key-a");
        assertThat(denied.allowed()).isFalse();
        assertThat(denied.retryAfter()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void refillsAfterThePeriod() {
        limiter.tryConsume("key-a");
        limiter.tryConsume("key-a");
        assertThat(limiter.tryConsume("key-a").allowed()).isFalse();

        clock.advance(Duration.ofSeconds(10));

        assertThat(limiter.tryConsume("key-a").allowed()).isTrue();
        assertThat(limiter.tryConsume("key-a").allowed()).isFalse();
    }

    @Test
    void keepsASeparateBucketPerKey() {
        limiter.tryConsume("key-a");
        limiter.tryConsume("key-a");
        assertThat(limiter.tryConsume("key-a").allowed()).isFalse();
        assertThat(limiter.tryConsume("key-b").allowed()).isTrue();
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return Clock.fixed(instant, zone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
