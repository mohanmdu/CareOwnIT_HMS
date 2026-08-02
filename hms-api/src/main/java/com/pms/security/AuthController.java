package com.pms.security;

import com.pms.security.dto.ChangePasswordRequest;
import com.pms.security.dto.LoginRequest;
import com.pms.security.dto.LoginResponse;
import com.pms.tenant.DeploymentModeProperties;
import jakarta.validation.Valid;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final LoginService loginService;
    private final DeploymentModeProperties deploymentMode;

    public AuthController(LoginService loginService, DeploymentModeProperties deploymentMode) {
        this.loginService = loginService;
        this.deploymentMode = deploymentMode;
    }

    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        if (deploymentMode.isMultiTenant()) {
            return new LoginResponse(loginService.login(request.clientCode(), request.username(), request.password()));
        }
        return new LoginResponse(loginService.login(request.username(), request.password()));
    }

    @PostMapping("/change-password")
    public LoginResponse changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return new LoginResponse(loginService.changePassword(username, request.currentPassword(), request.newPassword()));
    }
}
