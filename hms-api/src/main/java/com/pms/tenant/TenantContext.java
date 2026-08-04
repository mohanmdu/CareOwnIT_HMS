package com.pms.tenant;

import java.util.function.Supplier;

/**
 * Which client's dedicated database the current thread should talk to - the
 * key TenantRoutingDataSource resolves on every connection acquisition (see
 * the "Database-per-Client Architecture" plan's Dynamic Database Routing
 * design). A ThreadLocal, not a request attribute, because DataSource
 * routing happens inside Hibernate/JDBC internals that have no HttpServletRequest
 * to read from - JwtAuthenticationFilter is what bridges the two, setting
 * this from the JWT's clientId claim alongside its existing
 * request.setAttribute("clientId", ...) (kept, for ModuleAuthorizationManager's
 * license check - a separate concern from DB routing).
 *
 * MUST be cleared after every use (see clear() callers) - Tomcat reuses
 * worker threads across unrelated requests, so a leaked value here would
 * route one client's later, unrelated request at another client's database.
 * This is exactly the failure mode the plan's Verification section calls
 * out for a dedicated concurrency test.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_CLIENT_ID = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long clientId) {
        CURRENT_CLIENT_ID.set(clientId);
    }

    public static Long get() {
        return CURRENT_CLIENT_ID.get();
    }

    public static void clear() {
        CURRENT_CLIENT_ID.remove();
    }

    /**
     * Runs action with clientId set as the current tenant, restoring
     * whatever was there before (usually nothing) afterwards - for the two
     * call sites that need to address a specific tenant DB outside the
     * normal per-request JWT flow: LoginService.login(clientCode, ...)
     * (resolving the tenant DB before any JWT exists) and
     * ClientAdminBootstrapService.bootstrap(...) (a Super Admin JWT, which
     * carries no clientId claim by design, writing into one specific
     * tenant's DB).
     */
    public static <T> T runAs(Long clientId, Supplier<T> action) {
        Long previous = CURRENT_CLIENT_ID.get();
        CURRENT_CLIENT_ID.set(clientId);
        try {
            return action.get();
        } finally {
            if (previous == null) {
                CURRENT_CLIENT_ID.remove();
            } else {
                CURRENT_CLIENT_ID.set(previous);
            }
        }
    }
}
