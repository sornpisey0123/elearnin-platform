package com.pisey.elearning.repository;

import com.pisey.elearning.entity.User;
import com.pisey.elearning.repository.projection.RoleCount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, Long>, JpaSpecificationExecutor<User> {

    Optional<User> findByKeycloakId(UUID keycloakId);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    /** Only roles that have users; callers zero-fill the rest. */
    @Query("select u.role as role, count(u) as count from User u group by u.role")
    List<RoleCount> countGroupedByRole();
}
