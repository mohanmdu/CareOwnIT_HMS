package com.pms.superadmin;

import com.pms.security.dto.LoginRequest;
import com.pms.security.dto.LoginResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Wholly separate login endpoint/JWT shape from com.pms.security.AuthController
 * (see the multi-tenant licensing plan §A.5) - a super-admin JWT has no
 * modules/clientId claims and can never satisfy ModuleAuthorizationManager's
 * tenant checks, and vice versa. Public by entry (see
 * ModulePathMappings.PUBLIC_PREFIXES); reuses LoginRequest/LoginResponse
 * as-is, ignoring the clientCode field that only tenant login uses.
 */
@RestController
@RequestMapping("/api/super-admin/auth")
public class SuperAdminAuthController {

    private final SuperAdminLoginService loginService;

    public SuperAdminAuthController(SuperAdminLoginService loginService) {
        this.loginService = loginService;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return new LoginResponse(loginService.login(request.username(), request.password()));
    }
}
