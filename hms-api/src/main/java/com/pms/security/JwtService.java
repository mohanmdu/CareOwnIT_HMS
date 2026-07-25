package com.pms.security;

import com.pms.masters.entity.GeneralUser;
import com.pms.masters.entity.ModuleKey;
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

    public JwtService(
            @Value("${app.security.jwt-secret}") String secret,
            @Value("${app.security.jwt-expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
        this.expirationMinutes = expirationMinutes;
    }

    public String issue(GeneralUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plus(expirationMinutes, ChronoUnit.MINUTES);
        List<String> modules = user.getRole().getPermittedModules().stream().map(ModuleKey::key).toList();
        List<String> routes = List.copyOf(user.getRole().getPermittedRoutes());
        return Jwts.builder()
                .subject(user.getUsername())
                .claim("roleName", user.getRole().getName())
                .claim("modules", modules)
                .claim("routes", routes)
                // null removes the claim entirely (jjwt JwtBuilder semantics) rather than serializing a literal null -
                // most roles have no override, so this keeps their tokens clean.
                .claim("defaultRoute", user.getRole().getDefaultRoute())
                .claim("mustChangePassword", user.isMustChangePassword())
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
