package com.sgmart.auth;

import com.sgmart.auth.AdminUserDtos.AddAdminRequest;
import com.sgmart.auth.AdminUserDtos.AdminUserView;
import com.sgmart.auth.AdminUserDtos.UpdateAdminRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminUserController {

    private final AdminUserService service;

    public AdminUserController(AdminUserService service) {
        this.service = service;
    }

    /** The signed-in admin: name, email, role (used by the admin side menu). */
    @GetMapping("/me")
    public AdminUserView me(@AuthenticationPrincipal SgMartPrincipal admin) {
        return service.get(admin.getAppUserId());
    }

    @GetMapping("/admin-users")
    @PreAuthorize("hasRole('OWNER')")
    public List<AdminUserView> list() {
        return service.list();
    }

    @PostMapping("/admin-users")
    @PreAuthorize("hasRole('OWNER')")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminUserView add(@Valid @RequestBody AddAdminRequest request) {
        return service.add(request);
    }

    @PutMapping("/admin-users/{id}")
    @PreAuthorize("hasRole('OWNER')")
    public AdminUserView update(@PathVariable Long id, @Valid @RequestBody UpdateAdminRequest request) {
        return service.update(id, request);
    }
}
