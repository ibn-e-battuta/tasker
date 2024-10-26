package io.shinmen.app.tasker.config;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;

@Configuration
public class RateLimitConfig {

    @Value("${app.rate-limit.capacity}")
    private long capacity;

    @Value("${app.rate-limit.time}")
    private long time;

    @Value("${app.rate-limit.unit}")
    private String timeUnit;

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();

    public Bucket resolveBucket(String key) {
        return buckets.computeIfAbsent(key, this::newBucket);
    }

    private Bucket newBucket(String key) {
        Duration duration = Duration.of(time, ChronoUnit.valueOf(timeUnit));
        Bandwidth limit = Bandwidth.builder()
                .capacity(capacity)
                .refillGreedy(capacity, duration)
                .build();

        return Bucket.builder()
                .addLimit(limit)
                .build();
    }
}
