package com.sgmart.order;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.common.ApiException;
import com.sgmart.order.OrderDtos.CancelRequest;
import com.sgmart.order.OrderDtos.EditItemsRequest;
import com.sgmart.order.OrderDtos.OrderView;
import com.sgmart.order.OrderDtos.PlaceOrderRequest;
import com.sgmart.order.OrderDtos.PlacedOrder;
import com.sgmart.order.OrderDtos.TokensRequest;
import com.sgmart.protect.CaptchaVerifier;
import com.sgmart.protect.OrderRateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Customer order endpoints. Guests send the X-Tracking-Token header returned at checkout;
 * signed-in shoppers are recognised by their session.
 */
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    public static final String TOKEN_HEADER = "X-Tracking-Token";

    private final OrderService service;
    private final CaptchaVerifier captcha;
    private final OrderRateLimiter rateLimiter;

    public OrderController(OrderService service, CaptchaVerifier captcha, OrderRateLimiter rateLimiter) {
        this.service = service;
        this.captcha = captcha;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlacedOrder place(@Valid @RequestBody PlaceOrderRequest request,
                             @AuthenticationPrincipal SgMartPrincipal principal,
                             HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        rateLimiter.check(ip);
        if (!captcha.verify(request.captchaToken(), ip)) {
            throw ApiException.badRequest("CAPTCHA_FAILED", "Please complete the \"I'm not a robot\" check.", "captchaToken");
        }
        return service.place(request, customerId(principal));
    }

    @GetMapping("/{orderNo}")
    public OrderView get(@PathVariable String orderNo,
                         @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                         @AuthenticationPrincipal SgMartPrincipal principal) {
        return service.view(orderNo, token, customerId(principal));
    }

    /** Guest "My orders": POST the tracking tokens saved in this browser. */
    @PostMapping("/lookup")
    public List<OrderView> lookup(@Valid @RequestBody TokensRequest request) {
        return service.lookup(request.trackingTokens());
    }

    /** "Add or remove items" while the order is waiting for the store. */
    @PutMapping("/{orderNo}/items")
    public OrderView editItems(@PathVariable String orderNo,
                               @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                               @AuthenticationPrincipal SgMartPrincipal principal,
                               @Valid @RequestBody EditItemsRequest request) {
        return service.editItems(orderNo, token, customerId(principal), request);
    }

    @PostMapping("/{orderNo}/cancel")
    public OrderView cancel(@PathVariable String orderNo,
                            @RequestHeader(name = TOKEN_HEADER, required = false) String token,
                            @AuthenticationPrincipal SgMartPrincipal principal,
                            @Valid @RequestBody CancelRequest request) {
        return service.cancel(orderNo, token, customerId(principal), request.reason());
    }

    /** Only a signed-in shopper counts as the customer; an admin session places orders as a guest. */
    private static Long customerId(SgMartPrincipal principal) {
        return principal != null && principal.getKind() == SgMartPrincipal.Kind.CUSTOMER ? principal.getAppUserId() : null;
    }
}
