package com.sgmart.customer;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.customer.CustomerDtos.CustomerProfile;
import com.sgmart.customer.CustomerDtos.UpdateProfileRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Signed-in shopper's own profile (only after "Continue with Google"). */
@RestController
@RequestMapping("/api/v1/me")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping
    public CustomerProfile get(@AuthenticationPrincipal SgMartPrincipal me) {
        return service.profile(me.getAppUserId());
    }

    @PutMapping
    public CustomerProfile update(@AuthenticationPrincipal SgMartPrincipal me,
                                  @Valid @RequestBody UpdateProfileRequest request) {
        return service.updateProfile(me.getAppUserId(), request);
    }
}
