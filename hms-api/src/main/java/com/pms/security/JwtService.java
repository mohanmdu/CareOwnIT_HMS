package com.pms.security;

import com.pms.masters.entity.GeneralUser;
import com.pms.masters.entity.ModuleKey;
import com.pms.tenant.entity.SuperAdminUser;
import com.pms.tenant.service.ClientLicenseService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Issues/validates the app's own JWTs - this app is both issuer and validator, no external IdP involved. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long expirationMinutes;
    private final ClientLicenseService licenseService;

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-minutes}") long expirationMinutes,
            ClientLicenseService licenseService) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
        this.licenseService = licenseService;
    }

    /**
     * clientId is passed explicitly, not read off GeneralUser - Client now
     * lives in the master database (Phase B), a separate EntityManagerFactory
     * from GeneralUser's tenant one, so no @ManyToOne can span the two (see
     * the "Database-per-Client Architecture" plan's "Entity-level
     * consequence"). The caller (LoginService) resolves it via ClientRepository.
     */
    public String issue(GeneralUser user, Long clientId) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        List<String> modules = user.getRole().getPermittedModules().stream().map(ModuleKey::key).toList();
        List<String> routes = List.copyOf(user.getRole().getPermittedRoutes());
        // licensedModules is UI convenience only (nav/role-picker rendering) -
        // the actual enforcement decision (ModuleAuthorizationManager) always
        // re-checks ClientLicenseService fresh, never trusts this claim. See
        // the multi-tenant licensing plan's "License freshness" decision.
        List<String> licensedModules =
                licenseService.licensedModuleKeys(clientId).stream().map(ModuleKey::key).toList();
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roleName", user.getRole().getName())
                .claim("modules", modules)
                .claim("routes", routes)
                // null removes the claim entirely (jjwt JwtBuilder semantics) rather than serializing a literal null -
                // most roles have no override, so this keeps their tokens clean.
                .claim("defaultRoute", user.getRole().getDefaultRoute())
                .claim("mustChangePassword", user.isMustChangePassword())
                .claim("clientId", clientId)
                .claim("licensedModules", licensedModules)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    /**
     * Wholly separate claim shape from issue(GeneralUser) above - no
     * modules/clientId/routes at all, just a superAdmin flag. See
     * ModuleAuthorizationManager's SUPER_ADMIN branch and
     * JwtAuthenticationFilter for how this is turned into an authority that
     * a tenant JWT can never carry, and vice versa.
     */
    public String issueSuperAdmin(SuperAdminUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("superAdmin", true)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public Claims parse(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (JwtException | IllegalArgumentException e) {
            throw new InvalidCredentialsException("Invalid or expired session - please log in again.");
        }
    }
}
