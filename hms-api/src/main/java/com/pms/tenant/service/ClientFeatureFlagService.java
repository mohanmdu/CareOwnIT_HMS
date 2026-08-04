package com.pms.tenant.service;

import com.pms.tenant.repository.ClientFeatureFlagRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Data-driven on/off (optionally parameterized) switch check - see the
 * "Database-per-Client Architecture" plan's "Feature Flags & Client-
 * Specific Behavior" section and ClientFeatureFlag's own doc comment.
 * Same shape as ClientLicenseService: short-TTL cached (see CacheConfig),
 * checked fresh rather than trusted from any client-held state - a flag
 * is a server-side implementation-selection decision, never something the
 * frontend/JWT should carry an opinion about.
 *
 * Explicitly qualified to masterTransactionManager - see ClientService's
 * own doc comment for why (tenant holds @Primary now).
 */
@Service
@Transactional(readOnly = true, transactionManager = "masterTransactionManager")
public class ClientFeatureFlagService {

    private final ClientFeatureFlagRepository repository;

    public ClientFeatureFlagService(ClientFeatureFlagRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "clientFeatureFlag", cacheManager = "licenseCacheManager")
    public boolean isEnabled(Long clientId, String flagKey) {
        return repository.findByIdClientIdAndIdFlagKey(clientId, flagKey).map(flag -> flag.isEnabled()).orElse(false);
    }
}
