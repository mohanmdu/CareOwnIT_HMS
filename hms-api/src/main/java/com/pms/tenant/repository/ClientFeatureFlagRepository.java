package com.pms.tenant.repository;

import com.pms.tenant.entity.ClientFeatureFlag;
import com.pms.tenant.entity.ClientFeatureFlagId;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientFeatureFlagRepository extends JpaRepository<ClientFeatureFlag, ClientFeatureFlagId> {
    Optional<ClientFeatureFlag> findByIdClientIdAndIdFlagKey(Long clientId, String flagKey);
}
