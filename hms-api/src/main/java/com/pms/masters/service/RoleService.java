package com.pms.masters.service;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.dto.RoleDto;
import com.pms.masters.entity.ModuleKey;
import com.pms.masters.entity.Role;
import com.pms.masters.repository.RoleRepository;
import com.pms.tenant.repository.ClientRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class RoleService {

    private final RoleRepository repository;
    private final ClientRepository clientRepository;
    private final HttpServletRequest request;

    public RoleService(RoleRepository repository, ClientRepository clientRepository, HttpServletRequest request) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.request = request;
    }

    public List<RoleDto> findAll() {
        return repository.findByClientId(currentClientId()).stream().map(this::toDto).toList();
    }

    public RoleDto findById(Long id) {
        return toDto(getOrThrow(id));
    }

    @Transactional
    public RoleDto create(RoleDto dto) {
        Long clientId = currentClientId();
        if (repository.existsByClientIdAndNameIgnoreCase(clientId, dto.name())) {
            throw new IllegalArgumentException("Role already exists: " + dto.name());
        }
        Role role = new Role();
        role.setClient(clientRepository.getReferenceById(clientId));
        role.setName(dto.name());
        role.setActive(true);
        role.setPermittedModules(toModuleKeys(dto.permittedModules()));
        role.setPermittedRoutes(toRoutes(dto.permittedRoutes()));
        role.setDefaultRoute(blankToNull(dto.defaultRoute()));
        return toDto(repository.save(role));
    }

    @Transactional
    public RoleDto update(Long id, RoleDto dto) {
        Role role = getOrThrow(id);
        if (!role.getName().equalsIgnoreCase(dto.name())
                && repository.existsByClientIdAndNameIgnoreCase(role.getClient().getId(), dto.name())) {
            throw new IllegalArgumentException("Role already exists: " + dto.name());
        }
        role.setName(dto.name());
        role.setPermittedModules(toModuleKeys(dto.permittedModules()));
        role.setPermittedRoutes(toRoutes(dto.permittedRoutes()));
        role.setDefaultRoute(blankToNull(dto.defaultRoute()));
        return toDto(repository.save(role));
    }

    @Transactional
    public void deactivate(Long id) {
        Role role = getOrThrow(id);
        role.setActive(false);
        repository.save(role);
    }

    /** See com.pms.masters.service.GeneralUserService.currentClientId() - same reasoning: straight from the verified JWT claim, never re-derived by a lookup that could be ambiguous across clients. */
    private Long currentClientId() {
        Long clientId = (Long) request.getAttribute("clientId");
        if (clientId == null) {
            throw new IllegalStateException("No client context on this request.");
        }
        return clientId;
    }

    /** Every id-based operation (findById/update/deactivate) funnels through here - see GeneralUserService.getOrThrow() for why this is a 404, not a 403. */
    private Role getOrThrow(Long id) {
        Role role = repository.findById(id).orElseThrow(() -> new EntityNotFoundException("Role not found: " + id));
        if (!role.getClient().getId().equals(currentClientId())) {
            throw new EntityNotFoundException("Role not found: " + id);
        }
        return role;
    }

    /**
     * Dashboard (ModuleKey.OVERVIEW) is always granted, regardless of what
     * the caller submits - the Role screen deliberately shows no checkbox
     * for it (see role-list.component.ts), since a role with no landing page
     * would strand its users immediately after login in an unresolvable
     * redirect loop (every route requires a module, and there'd be nowhere
     * left to redirect to). Enforced here, the one place every persisted
     * Role's permittedModules passes through, rather than in the
     * authorization/guard layers - those just read whatever this set
     * contains.
     */
    private Set<ModuleKey> toModuleKeys(List<String> keys) {
        Set<ModuleKey> modules = new HashSet<>();
        if (keys != null) {
            keys.stream().map(ModuleKey::fromKey).forEach(modules::add);
        }
        modules.add(ModuleKey.OVERVIEW);
        return modules;
    }

    /** Opaque pass-through - see Role.permittedRoutes for why this backend doesn't validate against a canonical route list. */
    private Set<String> toRoutes(List<String> routes) {
        return routes == null ? new HashSet<>() : new HashSet<>(routes);
    }

    private String blankToNull(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }

    private RoleDto toDto(Role role) {
        List<String> permittedModules = role.getPermittedModules().stream().map(ModuleKey::key).toList();
        List<String> permittedRoutes = List.copyOf(role.getPermittedRoutes());
        return new RoleDto(
                role.getId(), role.getName(), role.isActive(), permittedModules, permittedRoutes, role.getDefaultRoute());
    }
}
