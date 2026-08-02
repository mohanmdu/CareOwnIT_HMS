package com.pms.tenant.repository;

import com.pms.tenant.entity.SuperAdminUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SuperAdminUserRepository extends JpaRepository<SuperAdminUser, Long> {
    Optional<SuperAdminUser> findByUsernameIgnoreCase(String username);
}
