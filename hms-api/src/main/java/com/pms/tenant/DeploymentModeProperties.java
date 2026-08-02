package com.pms.tenant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Which login flow this deployment uses (see the multi-tenant licensing
 * plan §A.8). Defaults to single-tenant so every existing/offline/on-prem
 * install upgrades with zero visible change - same 2-field login, one
 * auto-seeded, fully-licensed Client; only the shared multi-tenant VPS sets
 * DEPLOYMENT_MODE=multi-tenant for the 3-field client-code login. Note this
 * is deliberately narrow in scope: actual license enforcement
 * (ModuleAuthorizationManager, ClientLicenseService) never branches on this
 * flag, since a single-tenant install's one seeded Client is simply always
 * fully licensed - only the login entry point differs.
 */
@Component
public class DeploymentModeProperties {

    private final boolean multiTenant;

    public DeploymentModeProperties(@Value("${app.deployment.mode:single-tenant}") String mode) {
        this.multiTenant = "multi-tenant".equalsIgnoreCase(mode);
    }

    public boolean isMultiTenant() {
        return multiTenant;
    }
}
