package com.pms.tenant.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Composite key matching client_feature_flag's PRIMARY KEY (client_id, flag_key) - see ClientFeatureFlag. */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
public class ClientFeatureFlagId implements Serializable {

    @Column(name = "client_id")
    private Long clientId;

    @Column(name = "flag_key")
    private String flagKey;

    public ClientFeatureFlagId(Long clientId, String flagKey) {
        this.clientId = clientId;
        this.flagKey = flagKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof ClientFeatureFlagId other)) {
            return false;
        }
        return Objects.equals(clientId, other.clientId) && Objects.equals(flagKey, other.flagKey);
    }

    @Override
    public int hashCode() {
        return Objects.hash(clientId, flagKey);
    }
}
