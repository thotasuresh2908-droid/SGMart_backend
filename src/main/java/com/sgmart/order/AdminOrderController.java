package com.sgmart.order;

import com.sgmart.auth.SgMartPrincipal;
import com.sgmart.common.PageResult;
import com.sgmart.order.OrderDtos.AcceptRequest;
import com.sgmart.order.OrderDtos.AdminOrderView;
import com.sgmart.order.OrderDtos.DeliverRequest;
import com.sgmart.order.OrderDtos.DispatchRequest;
import com.sgmart.order.OrderDtos.OrderCounts;
import com.sgmart.order.OrderDtos.RejectRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** Admin > Orders. */
@RestController
@RequestMapping("/api/v1/admin/orders")
public class AdminOrderController {

    private final AdminOrderService service;
    private final OrderDeliveryService delivery;

    public AdminOrderController(AdminOrderService service, OrderDeliveryService delivery) {
        this.service = service;
        this.delivery = delivery;
    }

    /** /api/v1/admin/orders?status=PLACED&date=2026-11-12&page=0&size=20 */
    @GetMapping
    public PageResult<AdminOrderView> list(@RequestParam(name = "status", required = false) OrderStatus status,
                                           @RequestParam(name = "date", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
                                           @RequestParam(name = "page", defaultValue = "0") int page,
                                           @RequestParam(name = "size", defaultValue = "20") int size) {
        return service.list(status, date, page, size);
    }

    @GetMapping("/counts")
    public OrderCounts counts() {
        return service.counts();
    }

    @GetMapping("/{orderNo}")
    public AdminOrderView get(@PathVariable String orderNo) {
        return service.get(orderNo);
    }

    /** Body: { "unavailableItems": [ { "itemId": 12, "reason": "Out of stock" } ], "customerCallConfirmed": true } */
    @PostMapping("/{orderNo}/accept")
    public AdminOrderView accept(@PathVariable String orderNo, @Valid @RequestBody AcceptRequest request,
                                 @AuthenticationPrincipal SgMartPrincipal admin) {
        return service.accept(orderNo, request, admin.getAppUserId());
    }

    @PostMapping("/{orderNo}/reject")
    public AdminOrderView reject(@PathVariable String orderNo, @Valid @RequestBody RejectRequest request,
                                 @AuthenticationPrincipal SgMartPrincipal admin) {
        return service.reject(orderNo, request.reason(), admin.getAppUserId());
    }

    /** "Send out for delivery". Body: { "partnerId": 1 } */
    @PostMapping("/{orderNo}/dispatch")
    public AdminOrderView dispatch(@PathVariable String orderNo, @Valid @RequestBody DispatchRequest request,
                                   @AuthenticationPrincipal SgMartPrincipal admin) {
        return delivery.dispatch(orderNo, request.partnerId(), admin.getAppUserId());
    }

    /**
     * "Mark as delivered" with payment confirmation.
     * Body: { "method": "UPI", "paidByName": "Priya Sharma", "collectedByPartnerId": 1, "upiRef": "4821 9930 7712" }
     */
    @PostMapping("/{orderNo}/deliver")
    public AdminOrderView deliver(@PathVariable String orderNo, @Valid @RequestBody DeliverRequest request,
                                  @AuthenticationPrincipal SgMartPrincipal admin) {
        return delivery.deliver(orderNo, request, admin.getAppUserId());
    }
}
