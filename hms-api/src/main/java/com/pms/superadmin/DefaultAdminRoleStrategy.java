package com.pms.superadmin;

import com.pms.masters.entity.ModuleKey;
import java.util.Set;

/**
 * Which modules a brand-new client's first (bootstrap) Administrator role
 * gets - the "deeper per-client customization, not just on/off" case from
 * the "Database-per-Client Architecture" plan's "Feature Flags &
 * Client-Specific Behavior" section (its own illustrative example was a
 * BillingCalculationStrategy; this is this codebase's actual, concrete
 * instance of that same shape: a Strategy interface, Spring-qualified
 * bean implementations, and a resolver keyed off client_feature_flag -
 * see DefaultAdminRoleStrategyResolver).
 */
public interface DefaultAdminRoleStrategy {
    Set<ModuleKey> permittedModules();
}
