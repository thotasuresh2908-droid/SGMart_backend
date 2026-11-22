package com.sgmart.customer;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.Instant;

public final class CustomerDtos {

    private CustomerDtos() {
    }

    public record CustomerProfile(
            Long id,
            String name,
            String email,
            String mobile,
            boolean mobileVerified,
            int deliveredOrders) {

        static CustomerProfile of(Customer c) {
            return new CustomerProfile(c.getId(), c.getName(), c.getEmail(), c.getMobile(),
                    c.getVerifiedVia() == VerifiedVia.STORE_CALL, c.getDeliveredOrdersCount());
        }
    }

    /** What admins see about the customer on an order card. */
    public record CustomerAdminView(
            Long id,
            String name,
            String mobile,
            String email,
            boolean newCustomer,
            boolean mobileVerified,
            int deliveredOrders,
            boolean blocked,
            String blockedReason,
            Instant blockedAt) {

        public static CustomerAdminView of(Customer c) {
            return new CustomerAdminView(c.getId(), c.getName(), c.getMobile(), c.getEmail(), c.isNewCustomer(),
                    c.getVerifiedVia() == VerifiedVia.STORE_CALL, c.getDeliveredOrdersCount(), c.isBlocked(),
                    c.getBlockedReason(), c.getBlockedAt());
        }
    }

    public record BlockRequest(
            @NotBlank(message = "Enter why this number is blocked.")
            @Size(max = 255, message = "Keep the reason under 255 characters.")
            String reason) {
    }

    /** Mobile may be left empty to keep the current one. */
    public record UpdateProfileRequest(
            @NotBlank(message = "Enter your name.")
            @Size(min = 2, max = 100, message = "Name must be 2 to 100 characters.")
            String name,
            String mobile) {
    }
}
