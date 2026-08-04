package com.pms.security;

import com.pms.masters.entity.GeneralUser;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.tenant.TenantContext;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientStatus;
import com.pms.tenant.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Deliberately not a full UserDetailsService/AuthenticationManager/
 * DaoAuthenticationProvider chain - that's the session-oriented Spring
 * Security idiom. This app just needs "issue me a token."
 *
 * Deliberately no CLASS-level @Transactional - GeneralUserRepository (tenant
 * EMF) and ClientRepository (master EMF, see login(clientCode, ...)) can't
 * share one transaction/manager (see the "Database-per-Client Architecture"
 * plan's dual-EntityManagerFactory design). Most methods below still need
 * their OWN tenant-scoped @Transactional, though - not for the master
 * ClientRepository call (that self-transacts independently via its own
 * transactionManagerRef, same as any Spring Data repository), but to keep
 * a tenant session open across user.getRole() - GeneralUser.role is LAZY,
 * and without a surrounding transaction here the tenant EntityManager from
 * repository.findByUsernameIgnoreCase() closes before that access,
 * throwing LazyInitializationException.
 *
 * login(clientCode, ...) is the one exception - it uses a programmatic
 * TransactionTemplate instead of @Transactional. TenantRoutingDataSource
 * (see the "Database-per-Client Architecture" plan) routes each connection
 * off TenantContext, but @Transactional's AOP proxy would begin the tenant
 * transaction - and so acquire its routed connection - BEFORE this
 * method's body runs, i.e. before the client (and therefore the tenant to
 * route to) is even known. Programmatic transaction management lets
 * TenantContext be set first.
 */
@Service
public class LoginService {

    /** Matches the row every deployment seeds at V87 - the only Client in single-tenant mode (see DeploymentModeProperties), used as the DB-routing fallback whenever no TenantContext is set (see TenantRoutingDataSource). */
    private static final String DEFAULT_CLIENT_CODE = "DEFAULT";

    private final GeneralUserRepository repository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final TransactionTemplate tenantReadOnlyTransaction;

    public LoginService(
            GeneralUserRepository repository,
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            PlatformTransactionManager tenantTransactionManager) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        // Unqualified PlatformTransactionManager resolves to the @Primary
        // tenant one (see TenantJpaConfig) - deliberate, this template is
        // only ever used for the tenant-side portion of login(clientCode, ...).
        this.tenantReadOnlyTransaction = new TransactionTemplate(tenantTransactionManager);
        this.tenantReadOnlyTransaction.setReadOnly(true);
    }

    /** Single-tenant mode (the default - see DeploymentModeProperties) - unchanged since before Phase A, safe as a global username lookup because that mode only ever has one Client. */
    @Transactional(readOnly = true)
    public String login(String username, String password) {
        GeneralUser user = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password."));
        if (!user.isActive()) {
            throw new InvalidCredentialsException("This account has been deactivated.");
        }
        if (!user.getRole().isActive()) {
            throw new InvalidCredentialsException("This account's role has been deactivated.");
        }
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password.");
        }
        return jwtService.issue(user, defaultClientId());
    }

    /**
     * Multi-tenant mode only (app.deployment.mode=multi-tenant) - resolves
     * the Client by its code first (master DB, unmanaged - self-transacts
     * via ClientRepository's own transactionManagerRef, see this class's
     * own doc comment), to validate the submitted client code and status.
     * Then routes to that specific client's dedicated database (see
     * TenantContext/TenantRoutingDataSource) for the actual credential
     * check - a login attempt for a client with no provisioned database
     * (status never reached READY) fails here with InvalidCredentialsException,
     * same as a wrong password would, rather than silently falling back to
     * another tenant's data.
     */
    public String login(String clientCode, String username, String password) {
        Client client = clientRepository.findByCodeIgnoreCase(clientCode)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid client code, username, or password."));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new InvalidCredentialsException("This organization's access has been suspended.");
        }
        return TenantContext.runAs(client.getId(), () -> tenantReadOnlyTransaction.execute(status -> {
            GeneralUser user = repository.findByUsernameIgnoreCase(username)
                    .orElseThrow(() -> new InvalidCredentialsException("Invalid client code, username, or password."));
            if (!user.isActive()) {
                throw new InvalidCredentialsException("This account has been deactivated.");
            }
            if (!user.getRole().isActive()) {
                throw new InvalidCredentialsException("This account's role has been deactivated.");
            }
            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new InvalidCredentialsException("Invalid client code, username, or password.");
            }
            return jwtService.issue(user, client.getId());
        }));
    }

    private Long defaultClientId() {
        return clientRepository.findByCodeIgnoreCase(DEFAULT_CLIENT_CODE)
                .orElseThrow(() -> new IllegalStateException("No '" + DEFAULT_CLIENT_CODE + "' client seeded in the master database."))
                .getId();
    }

    /**
     * Returns a freshly-issued token reflecting mustChangePassword=false -
     * the caller's existing token still encodes the old (true) value baked
     * in at login time, and JWTs are immutable once issued. Without this,
     * the client would keep presenting the stale token after any full page
     * reload (AuthService re-decodes whatever's in sessionStorage on
     * construction) and get bounced back into the forced-change screen
     * forever, even though the password was already changed.
     */
    @Transactional
    public String changePassword(String username, String currentPassword, String newPassword) {
        if (newPassword == null || newPassword.isBlank()) {
            throw new IllegalArgumentException("New password must not be blank.");
        }
        GeneralUser user = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid session - please log in again."));
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Current password is incorrect.");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setMustChangePassword(false);
        repository.save(user);
        // The caller is already authenticated (see ModuleAuthorizationManager's
        // CHANGE_PASSWORD_PATH check), so JwtAuthenticationFilter has already
        // set TenantContext from this same request's JWT clientId claim -
        // that's the real value, not a guess. defaultClientId() is only a
        // fallback for the theoretical case of this being called without
        // that filter having run.
        Long clientId = TenantContext.get();
        return jwtService.issue(user, clientId != null ? clientId : defaultClientId());
    }
}
