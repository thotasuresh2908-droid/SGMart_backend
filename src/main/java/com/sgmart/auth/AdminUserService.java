package com.sgmart.auth;

import com.sgmart.auth.AdminUserDtos.AddAdminRequest;
import com.sgmart.auth.AdminUserDtos.AdminUserView;
import com.sgmart.auth.AdminUserDtos.UpdateAdminRequest;
import com.sgmart.common.ApiException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class AdminUserService {

    private static final Logger log =
            LoggerFactory.getLogger(AdminUserService.class);

    private final AdminUserRepository repository;
    private final Clock clock;

    public AdminUserService(AdminUserRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    /**
     * Google sign-in check.
     * Returns only an active admin account.
     * Updates last_login_at when authentication succeeds.
     */
    @Transactional
    public Optional<AdminUser> recordSignIn(String email) {

        if (email == null || email.isBlank()) {
            log.warn("Admin sign-in rejected: email is missing.");
            return Optional.empty();
        }

        String normalizedEmail =
                email.trim().toLowerCase(Locale.ROOT);

        log.debug("Checking active admin account for Google sign-in.");

        Optional<AdminUser> result =
                repository.findByEmailIgnoreCaseAndActiveTrue(normalizedEmail);

        if (result.isEmpty()) {
            log.warn("Admin sign-in rejected: no active admin account found.");
            return Optional.empty();
        }

        AdminUser admin = result.get();

        admin.setLastLoginAt(Instant.now(clock));

        // Save explicitly so the timestamp update is persisted.
        AdminUser saved = repository.save(admin);

        log.info("Admin sign-in record updated successfully. Admin ID: {}",
                saved.getId());

        return Optional.of(saved);
    }

    @Transactional(readOnly = true)
    public AdminUserView get(Long id) {
        return AdminUserView.of(load(id));
    }

    @Transactional(readOnly = true)
    public List<AdminUserView> list() {
        return repository.findAllByOrderByRoleAscNameAsc()
                .stream()
                .map(AdminUserView::of)
                .toList();
    }

    @Transactional
    public AdminUserView add(AddAdminRequest request) {

        String email = request.email()
                .trim()
                .toLowerCase(Locale.ROOT);

        if (repository.findByEmailIgnoreCase(email).isPresent()) {
            throw ApiException.conflict(
                    "ADMIN_EXISTS",
                    "This email already has admin access."
            );
        }

        AdminUser saved = repository.save(
                new AdminUser(
                        email,
                        request.name().trim(),
                        request.role()
                )
        );

        return AdminUserView.of(saved);
    }

    /**
     * Changes name, role or active.
     * Never leaves the store without an active owner.
     */
    @Transactional
    public AdminUserView update(Long id, UpdateAdminRequest request) {

        AdminUser admin = load(id);

        boolean staysActiveOwner =
                request.active() && request.role() == AdminRole.OWNER;

        if (admin.isActiveOwner()
                && !staysActiveOwner
                && repository.countByRoleAndActiveTrue(AdminRole.OWNER) <= 1) {

            throw ApiException.conflict(
                    "LAST_OWNER",
                    "The store needs at least one active owner. Add another owner first."
            );
        }

        admin.setName(request.name().trim());
        admin.setRole(request.role());
        admin.setActive(request.active());

        return AdminUserView.of(admin);
    }

    private AdminUser load(Long id) {
        return repository.findById(id)
                .orElseThrow(() ->
                        ApiException.notFound("Admin not found."));
    }
}
