package com.sgmart.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AdminUserRepository extends JpaRepository<AdminUser, Long> {

    Optional<AdminUser> findByEmailIgnoreCase(String email);

    long countByRoleAndActiveTrue(AdminRole role);

    List<AdminUser> findAllByOrderByRoleAscNameAsc();
}
