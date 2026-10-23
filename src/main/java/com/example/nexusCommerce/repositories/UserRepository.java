package com.example.nexusCommerce.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.nexusCommerce.schema.Role;
import com.example.nexusCommerce.schema.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    // email is stored lowercase and the column collation is case-insensitive, so plain
    // equality matches uk_users_email instead of forcing a full table scan.
    boolean existsByEmail(String email);

    Optional<User> findByEmail(String email);

    long countByRole(Role role);

    long countByRoleAndEnabledTrue(Role role);

    // Admin user search matches either identity field; both columns are small enough that a
    // contains-scan on a paged admin screen is cheaper than another index.
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name, String email, Pageable pageable);
}
