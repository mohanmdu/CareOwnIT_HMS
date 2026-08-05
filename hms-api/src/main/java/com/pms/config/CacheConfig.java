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
 * TTL. clientLicense/clientFeatureFlag (see ClientLicenseService/
 * ClientFeatureFlagService) instead need a genuine short TTL - see the
 * multi-tenant licensing plan's "License freshness" decision - so a
 * revoked module/flag stops working within seconds even without relying
 * solely on an explicit evict call, which ConcurrentMapCacheManager cannot
 * do. Both share this one Caffeine manager/TTL - same freshness
 * requirement, no reason for two near-identical beans. @Primary keeps
 * every existing unqualified @Cacheable (i.e. ClinicSettingsService)
 * resolving to the same bean as before this class changed - zero impact
 * on it.
 *
 * publicBranding (see PublicBrandingService) rides on this same Caffeine
 * manager rather than getting a third bean - it needs the identical
 * short-TTL-self-expiring shape (an unauthenticated, cheap-to-call,
 * per-clientCode lookup should never go stale for more than a few seconds,
 * and unlike clinicSettings it's keyed by clientCode, not TenantContext, so
 * it can't share that cache's own explicit-evict-only lifecycle).
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
        CaffeineCacheManager manager = new CaffeineCacheManager("clientLicense", "clientFeatureFlag", "publicBranding");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(45, TimeUnit.SECONDS));
        return manager;
    }
}
