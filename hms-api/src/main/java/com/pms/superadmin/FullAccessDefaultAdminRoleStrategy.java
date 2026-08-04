package com.pms.superadmin;

import com.pms.masters.entity.ModuleKey;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Every module, not just what the client is currently licensed for - the
 * default, existing behavior (see DefaultAdminRoleStrategyResolver).
 * Granting the role everything is safe: the actual visible/usable set is
 * still capped by the client's license at both the nav layer
 * (activeModuleKeys()) and the API layer (ModuleAuthorizationManager) - a
 * role permitting a module the client isn't licensed for is simply a
 * no-op until Super Admin licenses it.
 */
@Component("fullAccessDefaultAdminRoleStrategy")
public class FullAccessDefaultAdminRoleStrategy implements DefaultAdminRoleStrategy {

    @Override
    public Set<ModuleKey> permittedModules() {
        return new HashSet<>(Arrays.asList(ModuleKey.values()));
    }
}
