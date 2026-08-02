package com.pms.masters.repository;

import com.pms.masters.entity.Role;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    // Unscoped - kept only because nothing client-facing calls it anymore;
    // do not reuse for anything reachable by a tenant request.
    boolean existsByNameIgnoreCase(String name);

    boolean existsByClientIdAndNameIgnoreCase(Long clientId, String name);

    Optional<Role> findByClientIdAndNameIgnoreCase(Long clientId, String name);

    List<Role> findByClientId(Long clientId);
}
