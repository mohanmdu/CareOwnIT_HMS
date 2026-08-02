package com.pms.superadmin;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.GeneralUser;
import com.pms.masters.entity.ModuleKey;
import com.pms.masters.entity.Role;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.masters.repository.RoleRepository;
import com.pms.superadmin.dto.ClientAdminBootstrapRequest;
import com.pms.superadmin.dto.ClientAdminBootstrapResponse;
import com.pms.tenant.entity.Client;
import com.pms.tenant.repository.ClientRepository;
import java.util.Arrays;
import java.util.HashSet;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Closes the one gap Phase A otherwise left: a brand-new client has zero
 * general_user rows, so nobody can log in to reach the ordinary General
 * Users screen and create the first one - and now that Role is per-client
 * too (V90), a fresh client also has zero roles to assign. This is
 * Super Admin-only (see ModuleAuthorizationManager's SUPER_ADMIN branch),
 * used exactly once per client at onboarding time; every user after the
 * first is created the normal way, by that first admin, through the
 * regular General Users screen.
 */
@Service
@Transactional(readOnly = true)
public class ClientAdminBootstrapService {

    private static final String DEFAULT_ADMIN_ROLE_NAME = "Administrator";

    private final ClientRepository clientRepository;
    private final RoleRepository roleRepository;
    private final GeneralUserRepository generalUserRepository;
    private final PasswordEncoder passwordEncoder;

    public ClientAdminBootstrapService(
            ClientRepository clientRepository,
            RoleRepository roleRepository,
            GeneralUserRepository generalUserRepository,
            PasswordEncoder passwordEncoder) {
        this.clientRepository = clientRepository;
        this.roleRepository = roleRepository;
        this.generalUserRepository = generalUserRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public ClientAdminBootstrapResponse bootstrap(Long clientId, ClientAdminBootstrapRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));

        if (generalUserRepository.existsByClientIdAndUsernameIgnoreCase(clientId, request.username())) {
            throw new IllegalArgumentException("Username already taken: " + request.username());
        }

        Role role = roleRepository.findByClientIdAndNameIgnoreCase(clientId, DEFAULT_ADMIN_ROLE_NAME)
                .orElseGet(() -> createDefaultAdministratorRole(client));

        GeneralUser user = new GeneralUser();
        user.setClient(client);
        user.setName(request.name());
        user.setMobileNumber(request.mobileNumber());
        user.setRole(role);
        user.setUsername(request.username());
        user.setActive(true);
        user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
        user.setMustChangePassword(true);
        GeneralUser saved = generalUserRepository.save(user);

        return new ClientAdminBootstrapResponse(saved.getId(), saved.getUsername(), role.getName());
    }

    /**
     * Every module, not just what the client is currently licensed for -
     * this mirrors V74's bootstrap tenant admin and the manual seeding done
     * for the first three test clients. Granting the role everything is
     * safe: the actual visible/usable set is still capped by the client's
     * license at both the nav layer (activeModuleKeys()) and the API layer
     * (ModuleAuthorizationManager) - a role permitting a module the client
     * isn't licensed for is simply a no-op until Super Admin licenses it.
     */
    private Role createDefaultAdministratorRole(Client client) {
        Role role = new Role();
        role.setClient(client);
        role.setName(DEFAULT_ADMIN_ROLE_NAME);
        role.setActive(true);
        role.setPermittedModules(new HashSet<>(Arrays.asList(ModuleKey.values())));
        return roleRepository.save(role);
    }
}
