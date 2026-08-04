package com.pms.tenant.repository;

import com.pms.tenant.entity.ClientDatabase;
import com.pms.tenant.entity.ClientDatabaseStatus;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientDatabaseRepository extends JpaRepository<ClientDatabase, Long> {
    /** The only lookup TenantDataSourceRegistry uses to resolve a pool - a row in any other status is never routed to. */
    Optional<ClientDatabase> findByClientIdAndStatus(Long clientId, ClientDatabaseStatus status);

    /** Used by TenantProvisioningService to check for/update an existing row regardless of status (e.g. retrying a FAILED provision). */
    Optional<ClientDatabase> findByClientId(Long clientId);

    /** The deploy-time migration sweep's worklist - see TenantFlywayRunner. */
    List<ClientDatabase> findAllByStatus(ClientDatabaseStatus status);
}
