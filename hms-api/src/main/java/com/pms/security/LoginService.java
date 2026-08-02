package com.pms.security;

import com.pms.masters.entity.GeneralUser;
import com.pms.masters.repository.GeneralUserRepository;
import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientStatus;
import com.pms.tenant.repository.ClientRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deliberately not a full UserDetailsService/AuthenticationManager/
 * DaoAuthenticationProvider chain - that's the session-oriented Spring
 * Security idiom. This app just needs "issue me a token."
 */
@Service
@Transactional(readOnly = true)
public class LoginService {

    private final GeneralUserRepository repository;
    private final ClientRepository clientRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(
            GeneralUserRepository repository,
            ClientRepository clientRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.repository = repository;
        this.clientRepository = clientRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /** Single-tenant mode (the default - see DeploymentModeProperties) - unchanged since before Phase A, safe as a global username lookup because that mode only ever has one Client. */
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
        return jwtService.issue(user);
    }

    /** Multi-tenant mode only (app.deployment.mode=multi-tenant) - resolves the Client by its code first, since username is only unique within a Client, not globally. See the multi-tenant licensing plan §A.2. */
    public String login(String clientCode, String username, String password) {
        Client client = clientRepository.findByCodeIgnoreCase(clientCode)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid client code, username, or password."));
        if (client.getStatus() != ClientStatus.ACTIVE) {
            throw new InvalidCredentialsException("This organization's access has been suspended.");
        }
        GeneralUser user = repository.findByClientIdAndUsernameIgnoreCase(client.getId(), username)
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
        return jwtService.issue(user);
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
        return jwtService.issue(user);
    }
}
