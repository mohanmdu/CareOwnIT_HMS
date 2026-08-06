package com.pms.contact;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Hand-rolled per-IP counter rather than pulling in Bucket4j (not already on
 * the classpath, and not needed for a form this low-traffic) - Caffeine
 * already is (see com.pms.config.CacheConfig), just used directly here
 * instead of through Spring's @Cacheable abstraction, since this needs an
 * increment-and-read counter rather than a cache-aside read-through.
 *
 * A fixed window per IP, not a true sliding window - simple, and more than
 * adequate for deterring form-spam rather than precise API throttling.
 */
@Component
public class ContactRateLimiter {

    private final Cache<String, AtomicInteger> counts;
    private final int maxPerWindow;

    public ContactRateLimiter(
            @Value("${app.contact.rate-limit.max-per-window:5}") int maxPerWindow,
            @Value("${app.contact.rate-limit.window-minutes:60}") long windowMinutes) {
        this.maxPerWindow = maxPerWindow;
        this.counts =
                Caffeine.newBuilder().expireAfterWrite(windowMinutes, TimeUnit.MINUTES).build();
    }

    /** @throws RateLimitExceededException if this IP has already submitted maxPerWindow times within the current window. */
    public void checkAndRecord(String ipAddress) {
        AtomicInteger count = counts.asMap().computeIfAbsent(ipAddress, ip -> new AtomicInteger(0));
        if (count.incrementAndGet() > maxPerWindow) {
            throw new RateLimitExceededException("Too many requests. Please try again later.");
        }
    }
}
