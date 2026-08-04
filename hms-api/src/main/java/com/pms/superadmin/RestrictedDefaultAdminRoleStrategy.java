package com.pms.superadmin;

import com.pms.masters.entity.ModuleKey;
import java.util.EnumSet;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Only the core modules every client needs regardless of what they've
 * licensed so far (same set ClientService.ALWAYS_LICENSED always grants) -
 * for a client that would rather their first admin start narrow and add
 * modules deliberately, instead of landing with every module pre-granted.
 * Selected per-client via the "restricted-default-admin-role" feature flag
 * - see DefaultAdminRoleStrategyResolver.
 */
@Component("restrictedDefaultAdminRoleStrategy")
public class RestrictedDefaultAdminRoleStrategy implements DefaultAdminRoleStrategy {

    @Override
    public Set<ModuleKey> permittedModules() {
        return EnumSet.of(ModuleKey.OVERVIEW, ModuleKey.MASTERS, ModuleKey.ADMINISTRATION);
    }
}
