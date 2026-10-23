package com.pisey.elearning.repository;

import com.pisey.elearning.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);
}
