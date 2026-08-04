package com.pms.tenant;

import com.pms.tenant.entity.Client;
import com.pms.tenant.entity.ClientStatus;
import com.pms.tenant.repository.ClientRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves TenantContext from the request's Host header for traffic that
 * never carries a JWT - chiefly the public per-client hospital website
 * (com.pms.website's PublicXxxController family), which every visitor loads
 * unauthenticated. Without this, TenantContext stays unset for that traffic
 * and TenantRoutingDataSource falls back to whichever client is coded
 * DEFAULT (see TenantDataSourceRegistry.defaultClientId()) - every client's
 * public website showed the same (DEFAULT's) content regardless of which
 * client's actual domain the visitor hit.
 *
 * Runs before JwtAuthenticationFilter (see SecurityConfig) but only fills in
 * TenantContext when it's still unset by the time JwtAuthenticationFilter
 * runs - a real JWT's clientId claim always wins, this is purely a fallback
 * for requests that will never carry one.
 *
 * Single-tenant/on-prem installs (the overwhelming majority - see
 * DeploymentModeProperties) skip the lookup entirely: they have exactly one
 * Client row, already correctly resolved by TenantDataSourceRegistry's own
 * DEFAULT fallback, so a per-request master-DB domain query would be pure
 * overhead with no behavioral effect.
 */
public class DomainTenantResolutionFilter extends OncePerRequestFilter {

    private final ClientRepository clientRepository;
    private final DeploymentModeProperties deploymentMode;

    public DomainTenantResolutionFilter(ClientRepository clientRepository, DeploymentModeProperties deploymentMode) {
        this.clientRepository = clientRepository;
        this.deploymentMode = deploymentMode;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        try {
            if (deploymentMode.isMultiTenant()) {
                String host = hostOf(request);
                if (host != null) {
                    resolveByDomain(host).ifPresent(client -> TenantContext.set(client.getId()));
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            TenantContext.clear();
        }
    }

    /** X-Forwarded-Host first - production terminates TLS at a reverse proxy (see SecurityConfig's CORS doc comment), which forwards the original client-facing domain there rather than in Host. */
    private String hostOf(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-Host");
        String host = forwarded != null ? forwarded : request.getHeader("Host");
        if (host == null || host.isBlank()) {
            return null;
        }
        // Strip a port (e.g. "clienta-hospital.com:8080") and any comma-separated
        // extra hops a chain of proxies may have appended to X-Forwarded-Host.
        return host.split(",")[0].trim().split(":")[0].toLowerCase();
    }

    private Optional<Client> resolveByDomain(String host) {
        return clientRepository.findByDomainIgnoreCase(host).filter(client -> client.getStatus() == ClientStatus.ACTIVE);
    }
}
