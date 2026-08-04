package com.pms.security;

import com.pms.tenant.TenantContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Populates the SecurityContext from a valid Bearer JWT. Leaves it untouched
 * (falls through to Spring's default anonymous authentication) for a
 * missing/invalid token - ModuleAuthorizationManager denies either way, and
 * leaving the anonymous token in place is what makes Spring Security's
 * ExceptionTranslationFilter route "not logged in at all" to a 401 rather
 * than a 403 (see SecurityConfig's exception handling).
 *
 * Also sets TenantContext from the same clientId claim, alongside (not
 * replacing) the existing request.setAttribute("clientId", ...) - two
 * separate concerns that happen to read the same claim: the request
 * attribute drives ModuleAuthorizationManager's license check,
 * TenantContext drives TenantRoutingDataSource's connection routing (see
 * the "Database-per-Client Architecture" plan). MUST be cleared in a
 * finally block - Tomcat reuses worker threads across unrelated requests,
 * so a leaked value would route a later, unrelated request at the wrong
 * tenant's database.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    public JwtAuthenticationFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            String header = request.getHeader("Authorization");
            if (header != null && header.startsWith("Bearer ")) {
                try {
                    Claims claims = jwtService.parse(header.substring(7));
                    List<GrantedAuthority> authorities = new ArrayList<>();
                    if (Boolean.TRUE.equals(claims.get("superAdmin", Boolean.class))) {
                        // Wholly separate claim shape (see JwtService.issueSuperAdmin) -
                        // no modules/clientId here, so this branch never runs the
                        // tenant logic below at all, not just "happens to skip" it.
                        authorities.add(new SimpleGrantedAuthority("SUPER_ADMIN"));
                    } else {
                        List<?> modules = claims.get("modules", List.class);
                        if (modules != null) {
                            for (Object module : modules) {
                                authorities.add(new SimpleGrantedAuthority("MODULE_" + module));
                            }
                        }
                        if (Boolean.TRUE.equals(claims.get("mustChangePassword", Boolean.class))) {
                            authorities.add(new SimpleGrantedAuthority("MUST_CHANGE_PASSWORD"));
                        }
                        // Read by ModuleAuthorizationManager for the license check -
                        // see the multi-tenant licensing plan §A.4.
                        Long clientId = claims.get("clientId", Long.class);
                        if (clientId != null) {
                            request.setAttribute("clientId", clientId);
                            TenantContext.set(clientId);
                        }
                    }
                    var authentication = new UsernamePasswordAuthenticationToken(claims.getSubject(), null, authorities);
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                } catch (InvalidCredentialsException e) {
                    // Leave unauthenticated - falls through to the anonymous token, denied downstream.
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }
}
