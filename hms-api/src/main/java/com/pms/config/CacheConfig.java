package com.pms.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.concurrent.TimeUnit;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Two cache managers, deliberately: clinicSettings is read-forever-until-
 * explicitly-evicted (see ClinicSettingsService's @CacheEvict-on-write
 * pattern), which ConcurrentMapCacheManager already does correctly with no
 * TTL. clientLicense (see ClientLicenseService) instead needs a genuine
 * short TTL - see the multi-tenant licensing plan's "License freshness"
 * decision - so a revoked module stops working within seconds even without
 * relying solely on an explicit evict call, which ConcurrentMapCacheManager
 * cannot do. @Primary keeps every existing unqualified @Cacheable (i.e.
 * ClinicSettingsService) resolving to the same bean as before this class
 * changed - zero impact on it.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager("clinicSettings");
    }

    @Bean
    public CacheManager licenseCacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("clientLicense");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(45, TimeUnit.SECONDS));
        return manager;
    }
}
