package com.pms.superadmin;

import com.pms.superadmin.dto.ClientAdminBootstrapRequest;
import com.pms.superadmin.dto.ClientAdminBootstrapResponse;
import com.pms.superadmin.dto.ClientDto;
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

    public ClientController(ClientService service, ClientAdminBootstrapService bootstrapService) {
        this.service = service;
        this.bootstrapService = bootstrapService;
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
    public ClientDto updateStatus(@PathVariable Long id, @RequestBody Map<String, String> body) {
        return service.updateStatus(id, body.get("status"));
    }

    /** Body: { "lab": true, "pharmacy": false, ... } - kebab-case ModuleKey.key() values, same wire format as RoleDto.permittedModules. */
    @PutMapping("/{id}/modules")
    public ClientDto updateModules(@PathVariable Long id, @RequestBody Map<String, Boolean> modules) {
        return service.updateModules(id, modules);
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
}
