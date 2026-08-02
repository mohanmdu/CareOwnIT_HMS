package com.pms.tenant.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.ModuleKey;
import com.pms.tenant.entity.Client;
import com.pms.tenant.repository.ClientRepository;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The actual per-request license enforcement point (see
 * ModuleAuthorizationManager's second check) - always re-reads
 * client_module fresh via this short-TTL cache (see CacheConfig's
 * licenseCacheManager, ~45s TTL), never trusts the JWT's licensedModules
 * claim, which exists only for frontend UI convenience. Mirrors
 * ClinicSettingsService's read-heavy/cached shape, but with a real
 * expiry instead of evict-on-write only, since license revocation isn't
 * always driven through this same JVM (Super Admin's ClientService does
 * evict explicitly on write - see its @CacheEvict - but the TTL is the
 * backstop for "a revoked module stops working within seconds" even if
 * that ever isn't true).
 */
@Service
@Transactional(readOnly = true)
public class ClientLicenseService {

    private final ClientRepository repository;

    public ClientLicenseService(ClientRepository repository) {
        this.repository = repository;
    }

    @Cacheable(cacheNames = "clientLicense", cacheManager = "licenseCacheManager")
    public Set<ModuleKey> licensedModuleKeys(Long clientId) {
        Client client = repository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));
        return client.getModuleLicenses().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    public boolean isLicensed(Long clientId, ModuleKey moduleKey) {
        return licensedModuleKeys(clientId).contains(moduleKey);
    }
}
