package com.pms.config;

import com.pms.security.JwtAuthenticationFilter;
import com.pms.security.JwtService;
import com.pms.security.ModuleAuthorizationManager;
import com.pms.tenant.DeploymentModeProperties;
import com.pms.tenant.DomainTenantResolutionFilter;
import com.pms.tenant.repository.ClientRepository;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Real authentication (JWT, see com.pms.security) - replaces the interim
 * single-hardcoded-dev-user/permitAll setup this class used to have. Every
 * non-public request is authorized by ModuleAuthorizationManager, which
 * checks the requesting user's JWT-derived module authorities against
 * ModulePathMappings' {apiPrefix -> ModuleKey} table - centralized, rather
 * than @PreAuthorize sprinkled across 79 controllers.
 *
 * Anonymous authentication is deliberately left enabled (Spring Security's
 * default) - it's what makes ExceptionTranslationFilter route "no/invalid
 * token" to authenticationEntryPoint (401) rather than accessDeniedHandler
 * (403), which is reserved for "authenticated but lacking this module."
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final List<String> corsAllowedOrigins;

    public SecurityConfig(
            @Value("${app.security.cors-allowed-origins:http://localhost:*}") String corsAllowedOrigins) {
        this.corsAllowedOrigins =
                Arrays.stream(corsAllowedOrigins.split(",")).map(String::trim).toList();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public JwtAuthenticationFilter jwtAuthenticationFilter(JwtService jwtService) {
        return new JwtAuthenticationFilter(jwtService);
    }

    @Bean
    public DomainTenantResolutionFilter domainTenantResolutionFilter(
            ClientRepository clientRepository, DeploymentModeProperties deploymentMode) {
        return new DomainTenantResolutionFilter(clientRepository, deploymentMode);
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            DomainTenantResolutionFilter domainTenantResolutionFilter,
            ModuleAuthorizationManager moduleAuthorizationManager)
            throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().access(moduleAuthorizationManager))
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(domainTenantResolutionFilter, JwtAuthenticationFilter.class)
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> writeJsonError(
                                response, 401, "Authentication required."))
                        .accessDeniedHandler((request, response, accessDeniedException) -> writeJsonError(
                                response, 403, "You don't have access to this module.")));
        return http.build();
    }

    private void writeJsonError(jakarta.servlet.http.HttpServletResponse response, int status, String message)
            throws java.io.IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":" + status + ",\"message\":\"" + message + "\"}");
    }

    // Externalized via app.security.cors-allowed-origins (see
    // application.properties) rather than hardcoded, so the same jar works
    // in both places without a source change:
    //  - local dev default (http://localhost:*): a fixed origin list doesn't
    //    work here - when hms-web already holds 4200, the Angular/Vite dev
    //    server for hms-website falls back to a random free port instead of
    //    a predictable second one, so an allowlist of specific ports is a
    //    losing game.
    //  - production (CORS_ALLOWED_ORIGINS env var): a comma-separated list
    //    of the real deployed origins, e.g.
    //    https://careownitsolutions.com,https://admin.careownitsolutions.com
    // setAllowedOriginPatterns (not setAllowedOrigins) is required either
    // way to combine a wildcard/list with allowCredentials(true) - Spring
    // rejects a literal "*" origin under credentialed CORS, but a pattern is
    // evaluated per-request instead of matched statically, so it's allowed.
    private CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(corsAllowedOrigins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        configuration.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
