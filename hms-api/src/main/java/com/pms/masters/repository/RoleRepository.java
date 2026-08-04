package com.pms.masters.repository;

import com.pms.masters.entity.Role;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoleRepository extends JpaRepository<Role, Long> {
    boolean existsByNameIgnoreCase(String name);

    Optional<Role> findByNameIgnoreCase(String name);
}
