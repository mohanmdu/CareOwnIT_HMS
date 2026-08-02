package com.pms.superadmin;

import com.pms.security.InvalidCredentialsException;
import com.pms.security.JwtService;
import com.pms.tenant.entity.SuperAdminUser;
import com.pms.tenant.repository.SuperAdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Wholly separate credential store/JWT shape from com.pms.security.LoginService - see JwtService.issueSuperAdmin. */
@Service
@Transactional(readOnly = true)
public class SuperAdminLoginService {

    private final SuperAdminUserRepository repository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public SuperAdminLoginService(SuperAdminUserRepository repository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.repository = repository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    public String login(String username, String password) {
        SuperAdminUser user = repository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new InvalidCredentialsException("Invalid username or password."));
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new InvalidCredentialsException("Invalid username or password.");
        }
        return jwtService.issueSuperAdmin(user);
    }
}
