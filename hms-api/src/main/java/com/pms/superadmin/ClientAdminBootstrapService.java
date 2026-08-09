package com.pms.superadmin;

import com.pms.common.EntityNotFoundException;
import com.pms.masters.entity.GeneralUser;
import com.pms.masters.entity.Role;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.masters.repository.RoleRepository;
import com.pms.superadmin.dto.ClientAdminBootstrapRequest;
import com.pms.superadmin.dto.ClientAdminBootstrapResponse;
import com.pms.tenant.TenantContext;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientDatabaseStatus;
import com.pms.tenant.repository.ClientDatabaseRepository;
import com.pms.tenant.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Closes the one gap Phase A otherwise left: a brand-new client has zero
 * general_user rows, so nobody can log in to reach the ordinary General
 * Users screen and create the first one. This is Super Admin-only (see
 * ModuleAuthorizationManager's SUPER_ADMIN branch), used exactly once per
 * client at onboarding time; every user after the first is created the
 * normal way, by that first admin, through the regular General Users
 * screen.
 *
 * A Super Admin JWT carries no clientId claim by design (see JwtService.
 * issueSuperAdmin), so JwtAuthenticationFilter never sets TenantContext for
 * this request - this method is the one place that must set it explicitly
 * from the clientId parameter, routing the role/user write to that
 * specific client's dedicated database (see TenantRoutingDataSource).
 * Uses a programmatic TransactionTemplate rather than @Transactional for
 * the same reason as LoginService.login(clientCode, ...): the annotation's
 * AOP proxy would begin the tenant transaction - and so acquire its routed
 * connection - before TenantContext.runAs() below ever runs. The master-DB
 * Client read stays a separate, unmanaged, self-transacting lookup (see
 * ClientService's doc comment on why master and tenant can't share one
 * transaction) - acceptable here since it's read-only and doesn't need to
 * roll back together with the tenant-side insert.
 */
@Service
public class ClientAdminBootstrapService {

    private static final String DEFAULT_ADMIN_ROLE_NAME = "Administrator";

    private final ClientRepository clientRepository;
    private final ClientDatabaseRepository clientDatabaseRepository;
    private final RoleRepository roleRepository;
    private final GeneralUserRepository generalUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final DefaultAdminRoleStrategyResolver roleStrategyResolver;
    private final TransactionTemplate tenantTransaction;

    public ClientAdminBootstrapService(
            ClientRepository clientRepository,
            ClientDatabaseRepository clientDatabaseRepository,
            RoleRepository roleRepository,
            GeneralUserRepository generalUserRepository,
            PasswordEncoder passwordEncoder,
            DefaultAdminRoleStrategyResolver roleStrategyResolver,
            PlatformTransactionManager tenantTransactionManager) {
        this.clientRepository = clientRepository;
        this.clientDatabaseRepository = clientDatabaseRepository;
        this.roleRepository = roleRepository;
        this.generalUserRepository = generalUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.roleStrategyResolver = roleStrategyResolver;
        // Unqualified PlatformTransactionManager resolves to the @Primary
        // tenant one (see TenantJpaConfig).
        this.tenantTransaction = new TransactionTemplate(tenantTransactionManager);
    }

    public ClientAdminBootstrapResponse bootstrap(Long clientId, ClientAdminBootstrapRequest request) {
        Client client = clientRepository.findById(clientId)
                .orElseThrow(() -> new EntityNotFoundException("Client not found: " + clientId));

        // Checked here, before TenantContext.runAs()/the tenant transaction opens
        // a connection - TenantDataSourceRegistry throws this same exception type
        // deep inside JPA transaction-begin if skipped, but Spring wraps it in a
        // TransactionException there, which GlobalExceptionHandler's
        // EntityNotFoundException handler can't unwrap, so it fell through to the
        // generic 500 handler instead of a clean 404. Thrown from here, in the
        // normal method body, it's caught correctly.
        clientDatabaseRepository
                .findByClientIdAndStatus(clientId, ClientDatabaseStatus.READY)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Client " + clientId + " has no ready database yet - provision it before adding an admin."));

        return TenantContext.runAs(client.getId(), () -> tenantTransaction.execute(status -> {
            if (generalUserRepository.existsByUsernameIgnoreCase(request.username())) {
                throw new IllegalArgumentException("Username already taken: " + request.username());
            }

            Role role = roleRepository
                    .findByNameIgnoreCase(DEFAULT_ADMIN_ROLE_NAME)
                    .orElseGet(() -> createDefaultAdministratorRole(clientId));

            GeneralUser user = new GeneralUser();
            user.setName(request.name());
            user.setMobileNumber(request.mobileNumber());
            user.setRole(role);
            user.setUsername(request.username());
            user.setActive(true);
            user.setPasswordHash(passwordEncoder.encode(request.initialPassword()));
            user.setMustChangePassword(true);
            GeneralUser saved = generalUserRepository.save(user);

            return new ClientAdminBootstrapResponse(saved.getId(), saved.getUsername(), role.getName());
        }));
    }

    /**
     * Which modules this role gets is resolved per-client via
     * DefaultAdminRoleStrategyResolver (see its own doc comment) - the
     * default (FullAccessDefaultAdminRoleStrategy) mirrors V74's bootstrap
     * tenant admin and the manual seeding done for the first test clients:
     * every module, not just what the client is currently licensed for.
     * Granting the role everything is safe even then: the actual
     * visible/usable set is still capped by the client's license at both
     * the nav layer (activeModuleKeys()) and the API layer
     * (ModuleAuthorizationManager) - a role permitting a module the client
     * isn't licensed for is simply a no-op until Super Admin licenses it.
     */
    private Role createDefaultAdministratorRole(Long clientId) {
        Role role = new Role();
        role.setName(DEFAULT_ADMIN_ROLE_NAME);
        role.setActive(true);
        role.setPermittedModules(roleStrategyResolver.resolve(clientId).permittedModules());
        return roleRepository.save(role);
    }
}
