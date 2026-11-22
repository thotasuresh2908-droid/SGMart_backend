package com.sgmart.customer;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.customer.CustomerDtos.BlockRequest;
import com.sgmart.customer.CustomerDtos.CustomerAdminView;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** New-customer check and block list. */
@RestController
@RequestMapping("/api/v1/admin/customers")
public class AdminCustomerController {

    private final CustomerService service;

    public AdminCustomerController(CustomerService service) {
        this.service = service;
    }

    @GetMapping("/{id}")
    public CustomerAdminView get(@PathVariable Long id) {
        return service.adminView(id);
    }

    /** Numbers that can't place orders. */
    @GetMapping("/blocked")
    public List<CustomerAdminView> blocked() {
        return service.blocked();
    }

    /** "Mark verified" after calling the customer. */
    @PostMapping("/{id}/verify")
    public CustomerAdminView verify(@PathVariable Long id, @AuthenticationPrincipal SgMartPrincipal admin) {
        return service.markVerified(id, admin.getAppUserId());
    }

    @PostMapping("/{id}/block")
    public CustomerAdminView block(@PathVariable Long id, @Valid @RequestBody BlockRequest request) {
        return service.block(id, request.reason());
    }

    @PostMapping("/{id}/unblock")
    public CustomerAdminView unblock(@PathVariable Long id) {
        return service.unblock(id);
    }
}
