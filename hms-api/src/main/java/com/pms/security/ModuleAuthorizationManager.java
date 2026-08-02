package com.pms.security;

import com.pms.masters.entity.ModuleKey;
import com.pms.tenant.service.ClientLicenseService;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * Centralized request authorization - one place checking every request's
 * path against the module the authenticated user's role permits, instead of
 * @PreAuthorize sprinkled across 79 controllers (79 chances to forget one).
 * An unmapped path is denied by default with a warning log, not a hard
 * startup crash - a missed mapping should be loud and fixable, not able to
 * take the whole app down given how many endpoints exist.
 */
@Component
public class ModuleAuthorizationManager implements AuthorizationManager<RequestAuthorizationContext> {

    private static final Logger log = LoggerFactory.getLogger(ModuleAuthorizationManager.class);
    private static final String CHANGE_PASSWORD_PATH = "/api/auth/change-password";
    private static final String SUPER_ADMIN_PREFIX = "/api/super-admin/";

    private final ModulePathMappings pathMappings;
    private final ClientLicenseService clientLicenseService;

    public ModuleAuthorizationManager(ModulePathMappings pathMappings, ClientLicenseService clientLicenseService) {
        this.pathMappings = pathMappings;
        this.clientLicenseService = clientLicenseService;
    }

    @Override
    public AuthorizationDecision authorize(Supplier<? extends Authentication> authenticationSupplier, RequestAuthorizationContext context) {
        String path = context.getRequest().getRequestURI();

        if (ModulePathMappings.PUBLIC_PREFIXES.stream().anyMatch(path::startsWith)) {
            return new AuthorizationDecision(true);
        }

        Authentication authentication = authenticationSupplier.get();

        // Super Admin's world is structurally separate from the tenant
        // module-authorization pipeline below - a super-admin JWT carries no
        // modules/clientId claims at all (see JwtAuthenticationFilter), so it
        // could never satisfy the per-module check anyway, but branching
        // explicitly here means that's true by construction, not just by the
        // absence of a claim. See the multi-tenant licensing plan §A.5.
        if (path.startsWith(SUPER_ADMIN_PREFIX)) {
            boolean granted = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("SUPER_ADMIN"));
            return new AuthorizationDecision(granted);
        }

        // Changing your own password is module-independent - any real
        // (non-anonymous) authenticated user may call this regardless of
        // which modules their role permits, and regardless of whether
        // mustChangePassword is currently set (a user may also do this
        // voluntarily, not only when forced).
        if (path.equals(CHANGE_PASSWORD_PATH)) {
            return new AuthorizationDecision(!(authentication instanceof AnonymousAuthenticationToken));
        }

        boolean mustChangePassword =
                authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals("MUST_CHANGE_PASSWORD"));
        if (mustChangePassword) {
            return new AuthorizationDecision(false);
        }

        ModuleKey requiredModule = pathMappings.resolve(path);
        if (requiredModule == null) {
            log.warn("No ModulePathMappings entry for request path '{}' - denying by default. Add an entry to ModulePathMappings.", path);
            return new AuthorizationDecision(false);
        }

        String requiredAuthority = "MODULE_" + requiredModule.key();
        boolean roleGranted = authentication.getAuthorities().stream().anyMatch(a -> a.getAuthority().equals(requiredAuthority));
        if (!roleGranted) {
            return new AuthorizationDecision(false);
        }

        // Second axis: is this module actually licensed for the requesting
        // user's client at all - checked fresh on every request (short-TTL
        // cached), independent of what the role permits. Always active in
        // both deployment modes; a single-tenant install's one seeded Client
        // is simply always fully licensed, so this is a no-op there rather
        // than a special case. See the multi-tenant licensing plan §A.4/A.8.
        Long clientId = (Long) context.getRequest().getAttribute("clientId");
        boolean licensed = clientId != null && clientLicenseService.isLicensed(clientId, requiredModule);
        return new AuthorizationDecision(licensed);
    }
}
