package com.sgmart.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class AdminUserDtos {

    private AdminUserDtos() {
    }

    public record AdminUserView(Long id, String email, String name, AdminRole role, boolean active, Instant lastLoginAt) {
        static AdminUserView of(AdminUser a) {
            return new AdminUserView(a.getId(), a.getEmail(), a.getName(), a.getRole(), a.isActive(), a.getLastLoginAt());
        }
    }

    public record AddAdminRequest(
            @NotBlank(message = "Enter the Google email address.")
            @Email(message = "Enter a valid email address.")
            String email,
            @NotBlank(message = "Enter the person's name.")
            @Size(max = 120, message = "Keep the name under 120 characters.")
            String name,
            @NotNull(message = "Choose a role.")
            AdminRole role) {
    }

    public record UpdateAdminRequest(
            @NotBlank(message = "Enter the person's name.")
            @Size(max = 120, message = "Keep the name under 120 characters.")
            String name,
            @NotNull(message = "Choose a role.")
            AdminRole role,
            @NotNull(message = "Choose active or inactive.")
            Boolean active) {
    }
}
