package com.sgmart.order;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.order.OrderDtos.ClaimResult;
import com.sgmart.order.OrderDtos.OrderView;
import com.sgmart.order.OrderDtos.TokensRequest;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Signed-in shoppers (Continue with Google). */
@RestController
public class MyOrdersController {

    private final OrderService service;

    public MyOrdersController(OrderService service) {
        this.service = service;
    }

    @GetMapping("/api/v1/me/orders")
    public List<OrderView> myOrders(@AuthenticationPrincipal SgMartPrincipal me) {
        return service.myOrders(me.getAppUserId());
    }

    /** Right after sign-in: link guest orders saved in this browser to the account. */
    @PostMapping("/api/v1/me/claim-orders")
    public ClaimResult claim(@AuthenticationPrincipal SgMartPrincipal me, @Valid @RequestBody TokensRequest request) {
        return service.claim(me.getAppUserId(), request.trackingTokens());
    }
}
