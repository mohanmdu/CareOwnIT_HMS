package com.pms.contact;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

/**
 * Google reCAPTCHA v2/v3 server-side verification. Ships DISABLED by
 * default (app.contact.recaptcha.enabled=false) - fully wired so turning it
 * on later is a config-only change (flip the flag, set the secret key), not
 * a code change. Uses Spring's built-in RestClient (already transitively on
 * the classpath via spring-boot-starter-webmvc) rather than adding a new
 * HTTP client dependency for one outbound call.
 */
@Service
public class RecaptchaVerificationService {

    private static final Logger log = LoggerFactory.getLogger(RecaptchaVerificationService.class);
    private static final String VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";

    private final boolean enabled;
    private final String secretKey;
    private final double minScore;
    private final RestClient restClient = RestClient.create();

    public RecaptchaVerificationService(
            @Value("${app.contact.recaptcha.enabled:false}") boolean enabled,
            @Value("${app.contact.recaptcha.secret-key:}") String secretKey,
            @Value("${app.contact.recaptcha.min-score:0.5}") double minScore) {
        this.enabled = enabled;
        this.secretKey = secretKey;
        this.minScore = minScore;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Returns true when reCAPTCHA is disabled (nothing to check), or when Google confirms the token is valid and (for v3) scores above the configured threshold. Never throws - a verification failure just means "not verified", handled by the caller. */
    public boolean verify(String token) {
        if (!enabled) {
            return true;
        }
        if (token == null || token.isBlank()) {
            return false;
        }
        try {
            RecaptchaApiResponse response = restClient
                    .post()
                    .uri(VERIFY_URL + "?secret={secret}&response={token}", secretKey, token)
                    .retrieve()
                    .body(RecaptchaApiResponse.class);
            if (response == null || !response.success()) {
                return false;
            }
            // v2 responses carry no "score" field (null) - success alone is enough. v3
            // additionally scores 0.0-1.0 (confidence it's a real user); reject low scores.
            return response.score() == null || response.score() >= minScore;
        } catch (Exception ex) {
            log.warn("reCAPTCHA verification call failed - treating as not verified.", ex);
            return false;
        }
    }

    private record RecaptchaApiResponse(boolean success, Double score) {}
}
