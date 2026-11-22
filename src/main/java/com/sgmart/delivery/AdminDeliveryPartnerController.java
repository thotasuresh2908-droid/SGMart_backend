package com.sgmart.delivery;

import com.sgmart.delivery.DeliveryPartnerDtos.CreatePartnerRequest;
import com.sgmart.delivery.DeliveryPartnerDtos.PartnerView;
import com.sgmart.delivery.DeliveryPartnerDtos.StatusRequest;
import com.sgmart.delivery.DeliveryPartnerDtos.UpdatePartnerRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Admin > Delivery partners. */
@RestController
@RequestMapping("/api/v1/admin/delivery-partners")
public class AdminDeliveryPartnerController {

    private final DeliveryPartnerService service;

    public AdminDeliveryPartnerController(DeliveryPartnerService service) {
        this.service = service;
    }

    @GetMapping
    public List<PartnerView> list() {
        return service.list();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PartnerView create(@Valid @RequestBody CreatePartnerRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public PartnerView update(@PathVariable Long id, @Valid @RequestBody UpdatePartnerRequest request) {
        return service.update(id, request);
    }

    @PostMapping("/{id}/status")
    public PartnerView setStatus(@PathVariable Long id, @Valid @RequestBody StatusRequest request) {
        return service.setStatus(id, request.status());
    }
}
