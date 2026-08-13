package com.pms.superadmin;

import com.pms.superadmin.dto.ClientAdminBootstrapRequest;
import com.pms.superadmin.dto.ClientAdminBootstrapResponse;
import com.pms.superadmin.dto.ClientDatabaseDto;
import com.pms.superadmin.dto.ClientDomainStatusResponse;
import com.pms.superadmin.dto.ClientDomainUpdateRequest;
import com.pms.superadmin.dto.ClientDto;
import com.pms.superadmin.dto.ClientStatusUpdateRequest;
import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.service.TenantProvisioningService;
import com.pms.common.EntityNotFoundException;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Reachable only with a SUPER_ADMIN-authority JWT - see ModuleAuthorizationManager's SUPER_ADMIN_PREFIX branch. */
@RestController
@RequestMapping("/api/super-admin/clients")
public class ClientController {

    private final ClientService service;
    private final ClientAdminBootstrapService bootstrapService;
    private final TenantProvisioningService provisioningService;
    private final ClientDatabaseRepository clientDatabaseRepository;
    private final ClientDomainStatusService domainStatusService;

    public ClientController(
            ClientService service,
            ClientAdminBootstrapService bootstrapService,
            TenantProvisioningService provisioningService,
            ClientDatabaseRepository clientDatabaseRepository,
            ClientDomainStatusService domainStatusService) {
        this.service = service;
        this.bootstrapService = bootstrapService;
        this.provisioningService = provisioningService;
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.domainStatusService = domainStatusService;
    }

    @GetMapping
    public List<ClientDto> list() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public ClientDto get(@PathVariable Long id) {
        return service.findById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClientDto create(@Valid @RequestBody ClientDto dto) {
        return service.create(dto);
    }

    @PatchMapping("/{id}/status")
    public ClientDto updateStatus(@PathVariable Long id, @Valid @RequestBody ClientStatusUpdateRequest body) {
        return service.updateStatus(id, body.status().name());
    }

    /** Body: { "lab": true, "pharmacy": false, ... } - kebab-case ModuleKey.key() values, same wire format as RoleDto.permittedModules. */
    @PutMapping("/{id}/modules")
    public ClientDto updateModules(@PathVariable Long id, @RequestBody Map<String, Boolean> modules) {
        return service.updateModules(id, modules);
    }

    @PatchMapping("/{id}/domain")
    public ClientDto updateDomain(@PathVariable Long id, @Valid @RequestBody ClientDomainUpdateRequest body) {
        return service.updateDomain(id, body.domain());
    }

    /**
     * On-demand HTTPS reachability probe for this client's public-website
     * domain - see ClientDomainStatusService's own doc comment. Never
     * throws; NOT_SET/UNREACHABLE are both normal, expected responses, not
     * errors, since DNS/nginx/TLS provisioning is a separate manual step
     * (scripts/provision-client-subdomain.sh) that may not have happened yet.
     */
    @GetMapping("/{id}/domain/status")
    public ClientDomainStatusResponse domainStatus(@PathVariable Long id) {
        ClientDto client = service.findById(id);
        return new ClientDomainStatusResponse(domainStatusService.check(client.domain()));
    }

    /**
     * One-time bootstrap for a brand-new client's very first user - see
     * ClientAdminBootstrapService's own doc comment for why this exists.
     * Every subsequent user for this client is created the normal way, by
     * this first admin, through /api/masters/general-users.
     */
    @PostMapping("/{id}/admin")
    @ResponseStatus(HttpStatus.CREATED)
    public ClientAdminBootstrapResponse bootstrapAdmin(@PathVariable Long id, @Valid @RequestBody ClientAdminBootstrapRequest request) {
        return bootstrapService.bootstrap(id, request);
    }

    /**
     * Provisions this client's dedicated database - CREATE DATABASE/USER,
     * the full tenant Flyway migration set, then flips ClientDatabase.status
     * to READY (see TenantProvisioningService's own doc comment). Synchronous
     * and slow (a full 90+ migration run) by design - this is a rare,
     * Super-Admin-only onboarding action, not something to hide behind a
     * misleadingly-instant 202. Safe to call again for a client stuck in
     * FAILED.
     */
    @PostMapping("/{id}/database")
    public ClientDatabaseDto provisionDatabase(@PathVariable Long id) {
        return toDto(provisioningService.provision(id));
    }

    @GetMapping("/{id}/database")
    public ClientDatabaseDto getDatabase(@PathVariable Long id) {
        return clientDatabaseRepository
                .findByClientId(id)
                .map(this::toDto)
                .orElseThrow(() -> new EntityNotFoundException("No database provisioned yet for client " + id));
    }

    private ClientDatabaseDto toDto(ClientDatabase db) {
        return new ClientDatabaseDto(
                db.getClientId(), db.getHost(), db.getPort(), db.getSchemaName(), db.getStatus().name(), db.getSchemaVersion());
    }
}
