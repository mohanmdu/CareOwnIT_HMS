package com.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A data-driven on/off (optionally parameterized) switch for behavior
 * already compiled into the one app - see the "Database-per-Client
 * Architecture" plan's "Feature Flags & Client-Specific Behavior" section.
 * Same enforcement shape as ClientModule (client_module): checked fresh,
 * short-TTL cached, never trusted from any client-held state.
 *
 * configJson is a free-form JSON string, for flags that need more than a
 * boolean (e.g. a numeric threshold) - null for simple on/off flags. Kept
 * as a raw String rather than parsed here; a consuming strategy parses it
 * itself if and when it needs to, since different flags will want
 * different shapes.
 */
@Entity
@Table(name = "client_feature_flag")
@Getter
@Setter
@NoArgsConstructor
public class ClientFeatureFlag {

    @EmbeddedId
    private ClientFeatureFlagId id;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "config_json", columnDefinition = "json")
    private String configJson;

    public ClientFeatureFlag(Long clientId, String flagKey, boolean enabled) {
        this.id = new ClientFeatureFlagId(clientId, flagKey);
        this.enabled = enabled;
    }
}
