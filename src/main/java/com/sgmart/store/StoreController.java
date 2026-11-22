package com.sgmart.store;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.store.StoreDtos.StoreInfo;
import com.sgmart.store.StoreDtos.UpdateStoreRequest;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class StoreController {

    private final StoreSettingsService service;

    public StoreController(StoreSettingsService service) {
        this.service = service;
    }

    /** Public: store name, phone (Call store), hours, open now, ETA, delivery fee rules. */
    @GetMapping("/api/v1/store")
    public StoreInfo get() {
        return service.info();
    }

    /** Owner only: change store phone, hours, ETA, delivery fee and first-order cap. */
    @PutMapping("/api/v1/admin/store")
    @PreAuthorize("hasRole('OWNER')")
    public StoreInfo update(@Valid @RequestBody UpdateStoreRequest request,
                            @AuthenticationPrincipal SgMartPrincipal admin) {
        return service.update(request, admin.getAppUserId());
    }
}
