package com.pms.security;

import com.pms.masters.entity.GeneralUser;
import com.pms.masters.repository.GeneralUserRepository;
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
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public LoginService(GeneralUserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

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
