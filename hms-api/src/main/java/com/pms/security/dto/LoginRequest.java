package com.pms.security.dto;

import jakarta.validation.constraints.NotBlank;

// clientCode is only required in multi-tenant mode (see
// DeploymentModeProperties / AuthController) - left unvalidated here since a
// single-tenant-mode request never sends it. Also reused as-is by
// SuperAdminAuthController's login, which ignores it entirely.
public record LoginRequest(String clientCode, @NotBlank String username, @NotBlank String password) {
}
