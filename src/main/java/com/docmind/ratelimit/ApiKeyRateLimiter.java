package com.docmind.ratelimit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;

/**
 * One token bucket per API key. Cheap and local to this process: enough to stop
 * a single key from flooding the chat model, not a cluster-wide quota.
 */
@Component
public class ApiKeyRateLimiter {

    private final RateLimitProperties properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    @Autowired
    public ApiKeyRateLimiter(RateLimitProperties properties) {
        this(properties, Clock.systemUTC());
    }

    ApiKeyRateLimiter(RateLimitProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    public Decision tryConsume(String apiKey) {
        Instant now = clock.instant();
        return buckets.computeIfAbsent(apiKey, key -> new Bucket(properties, now)).tryConsume(now, properties);
    }

    public record Decision(boolean allowed, Duration retryAfter) {
        static Decision allow() {
            return new Decision(true, Duration.ZERO);
        }

        static Decision deny(Duration retryAfter) {
            return new Decision(false, retryAfter);
        }
    }

    static final class Bucket {

        private double tokens;
        private Instant lastRefill;

        Bucket(RateLimitProperties properties, Instant now) {
            this.tokens = properties.capacity();
            this.lastRefill = now;
        }

        synchronized Decision tryConsume(Instant now, RateLimitProperties properties) {
            refill(now, properties);
            if (tokens >= 1) {
                tokens -= 1;
                return Decision.allow();
            }
            double missing = 1 - tokens;
            double tokensPerNano = properties.refillTokens() / (double) properties.refillPeriod().toNanos();
            long waitNanos = (long) Math.ceil(missing / tokensPerNano);
            return Decision.deny(Duration.ofNanos(waitNanos));
        }

        private void refill(Instant now, RateLimitProperties properties) {
            long elapsed = now.toEpochMilli() - lastRefill.toEpochMilli();
            if (elapsed <= 0) {
                return;
            }
            double tokensPerMilli = properties.refillTokens() / (double) properties.refillPeriod().toMillis();
            tokens = Math.min(properties.capacity(), tokens + elapsed * tokensPerMilli);
            lastRefill = now;
        }
    }
}
