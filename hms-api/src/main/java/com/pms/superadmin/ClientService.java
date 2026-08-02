package com.pms.superadmin;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.ModuleKey;
import com.pms.superadmin.dto.ClientDto;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientStatus;
import com.pms.tenant.repository.ClientRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Super Admin-only (see ModuleAuthorizationManager's SUPER_ADMIN branch) - CRUD for Client and its module license. */
@Service
@Transactional(readOnly = true)
public class ClientService {

    /**
     * Always licensed for every client, never togglable off - without this,
     * Super Admin could license a client for e.g. Lab only and lock that
     * client's own admin out of /api/masters/roles and
     * /api/masters/general-users entirely (both map to ADMINISTRATION -
     * see ModulePathMappings), since the license check applies regardless
     * of role. Mirrors RoleService.toModuleKeys() always granting OVERVIEW.
     */
    private static final Set<ModuleKey> ALWAYS_LICENSED =
            Set.of(ModuleKey.OVERVIEW, ModuleKey.MASTERS, ModuleKey.ADMINISTRATION);

    private final ClientRepository repository;

    public ClientService(ClientRepository repository) {
        this.repository = repository;
    }

    public List<ClientDto> findAll() {
        return repository.findAll().stream().map(this::toDto).toList();
    }

    public ClientDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public ClientDto create(ClientDto dto) {
        if (repository.existsByCodeIgnoreCase(dto.code())) {
            throw new IllegalArgumentException("Client code already in use: " + dto.code());
        }
        Client client = new Client();
        client.setName(dto.name());
        client.setCode(dto.code().trim().toUpperCase());
        client.setStatus(ClientStatus.ACTIVE);
        ALWAYS_LICENSED.forEach(key -> client.getModuleLicenses().put(key, true));
        return toDto(repository.save(client));
    }

    @Transactional
    public ClientDto updateStatus(Long id, String status) {
        Client client = getOrThrow(id);
        client.setStatus(ClientStatus.valueOf(status));
        return toDto(repository.save(client));
    }

    /**
     * Full replace of the submitted keys' enabled state - the Super Admin
     * license editor always submits every module checkbox's current state,
     * same "submit the full current selection" semantics as
     * RoleService.update()'s permittedModules. Evicts the short-TTL license
     * cache immediately (see ClientLicenseService) rather than waiting out
     * its ~45s TTL, so a revoked module stops working right away.
     */
    @Transactional
    @CacheEvict(cacheNames = "clientLicense", cacheManager = "licenseCacheManager", key = "#id")
    public ClientDto updateModules(Long id, Map<String, Boolean> modules) {
        Client client = getOrThrow(id);
        modules.forEach((key, enabled) -> client.getModuleLicenses().put(ModuleKey.fromKey(key), enabled));
        ALWAYS_LICENSED.forEach(key -> client.getModuleLicenses().put(key, true));
        return toDto(repository.save(client));
    }

    private Client getOrThrow(Long id) {
        return repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Client not found: " + id));
    }

    private ClientDto toDto(Client client) {
        List<String> licensed = client.getModuleLicenses().entrySet().stream()
                .filter(Map.Entry::getValue)
                .map(entry -> entry.getKey().key())
                .toList();
        return new ClientDto(
                client.getId(), client.getName(), client.getCode(), client.getStatus().name(), licensed, client.getCreatedAt());
    }
}
