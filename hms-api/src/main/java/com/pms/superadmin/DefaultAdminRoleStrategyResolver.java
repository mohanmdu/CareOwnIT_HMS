package com.pms.superadmin;

import com.pms.tenant.service.ClientFeatureFlagService;
import org.springframework.stereotype.Component;

/**
 * Picks which DefaultAdminRoleStrategy bean a given client's bootstrap
 * uses, keyed off the "restricted-default-admin-role" client_feature_flag
 * (master DB, see ClientFeatureFlagService) - the resolver the
 * "Database-per-Client Architecture" plan's Strategy-pattern design
 * describes, made concrete. Every variant stays a normal Spring bean in
 * this same compiled/tested/deployed artifact - no runtime class loading,
 * no per-client build.
 */
@Component
public class DefaultAdminRoleStrategyResolver {

    private static final String RESTRICTED_FLAG_KEY = "restricted-default-admin-role";

    private final ClientFeatureFlagService featureFlagService;
    private final DefaultAdminRoleStrategy fullAccessStrategy;
    private final DefaultAdminRoleStrategy restrictedStrategy;

    public DefaultAdminRoleStrategyResolver(
            ClientFeatureFlagService featureFlagService,
            FullAccessDefaultAdminRoleStrategy fullAccessStrategy,
            RestrictedDefaultAdminRoleStrategy restrictedStrategy) {
        this.featureFlagService = featureFlagService;
        this.fullAccessStrategy = fullAccessStrategy;
        this.restrictedStrategy = restrictedStrategy;
    }

    public DefaultAdminRoleStrategy resolve(Long clientId) {
        return featureFlagService.isEnabled(clientId, RESTRICTED_FLAG_KEY) ? restrictedStrategy : fullAccessStrategy;
    }
}
