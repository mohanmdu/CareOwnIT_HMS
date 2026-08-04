package com.pms.masters.repository;

import com.pms.masters.entity.GeneralUser;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GeneralUserRepository extends JpaRepository<GeneralUser, Long> {
    List<GeneralUser> findByActiveTrueOrderByIdAsc();

    List<GeneralUser> findByActiveFalseOrderByUpdatedAtDesc();

    Optional<GeneralUser> findByUsernameIgnoreCase(String username);

    boolean existsByUsernameIgnoreCase(String username);
}
